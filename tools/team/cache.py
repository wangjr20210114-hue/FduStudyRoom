"""Repair private Linux runner caches from immutable, trusted image trees.

The program is streamed into a runner so updating it never requires rebuilding
the CI image. Only expected entries are synchronized; private extras remain.
"""
import errno
import hashlib
import os
from pathlib import Path
import shutil
import stat
import sys
import uuid


class CacheSafetyError(ValueError):
    """A cache path would follow a link or leave its intended tree."""


_DIRECTORY_FLAGS = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0)


def _copy_file(source, target):
    shutil.copyfileobj(source, target)


def _digest(handle):
    handle.seek(0)
    digest = hashlib.sha256()
    for block in iter(lambda: handle.read(1024 * 1024), b""):
        digest.update(block)
    return digest.digest()


def _open_child(parent, name):
    try:
        return os.open(name, _DIRECTORY_FLAGS, dir_fd=parent)
    except FileNotFoundError:
        try:
            os.mkdir(name, mode=0o777, dir_fd=parent)
        except FileExistsError:
            pass
        return os.open(name, _DIRECTORY_FLAGS, dir_fd=parent)
    except OSError as error:
        if error.errno in (errno.ELOOP, errno.ENOTDIR):
            raise CacheSafetyError("CACHE_DIRECTORY_MUST_NOT_BE_A_SYMLINK") from error
        raise


def _open_root(path):
    path = Path(os.path.abspath(path))
    descriptor = os.open(path.anchor, _DIRECTORY_FLAGS)
    try:
        for part in path.parts[1:]:
            child = _open_child(descriptor, part)
            os.close(descriptor)
            descriptor = child
        return descriptor
    except BaseException:
        os.close(descriptor)
        raise


def _open_relative(root, relative):
    descriptor = os.dup(root)
    try:
        for part in relative.parts:
            if part in (".", "..") or os.path.isabs(part):
                raise CacheSafetyError("CACHE_PATH_ESCAPE")
            child = _open_child(descriptor, part)
            os.close(descriptor)
            descriptor = child
        return descriptor
    except BaseException:
        os.close(descriptor)
        raise


def _inventory(source):
    directories, files, links = [], [], []
    def visit(directory):
        for entry in sorted(directory.iterdir()):
            relative = entry.relative_to(source)
            mode = entry.lstat().st_mode
            if stat.S_ISLNK(mode):
                target = os.readlink(entry)
                try:
                    resolved = entry.resolve(strict=True)
                except (OSError, RuntimeError) as error:
                    raise CacheSafetyError("CACHE_SOURCE_LINK_INVALID") from error
                if os.path.isabs(target) or not resolved.is_relative_to(source):
                    raise CacheSafetyError("CACHE_SOURCE_LINK_ESCAPE")
                links.append((relative, target))
            elif stat.S_ISDIR(mode):
                directories.append(relative)
                visit(entry)
            elif stat.S_ISREG(mode):
                files.append((relative, mode & 0o777))
            else:
                raise CacheSafetyError("CACHE_SOURCE_ENTRY_UNSUPPORTED")
    visit(source)
    return directories, files, links


def _matches(parent, name, expected, size, mode):
    try:
        descriptor = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=parent)
    except FileNotFoundError:
        return False
    except OSError as error:
        if error.errno == errno.ELOOP:
            return False
        raise
    with os.fdopen(descriptor, "rb") as handle:
        information = os.fstat(handle.fileno())
        if not stat.S_ISREG(information.st_mode):
            raise CacheSafetyError("CACHE_FILE_TARGET_MUST_BE_REGULAR")
        if information.st_size != size or _digest(handle) != expected:
            return False
        os.fchmod(handle.fileno(), mode)
        return True


def _repair_file(source, parent, name, mode):
    # Cache files remain writable by the unprivileged member; original execute
    # bits are preserved, and source setuid/setgid bits are never propagated.
    mode |= 0o666
    with os.fdopen(os.open(source, os.O_RDONLY | os.O_NOFOLLOW), "rb") as original:
        expected = _digest(original)
        if _matches(parent, name, expected, os.fstat(original.fileno()).st_size, mode):
            return False
        temporary = ".campus-cache-" + uuid.uuid4().hex + ".tmp"
        descriptor = os.open(temporary, os.O_CREAT | os.O_EXCL | os.O_RDWR | os.O_NOFOLLOW,
                             0o600, dir_fd=parent)
        try:
            with os.fdopen(descriptor, "w+b") as candidate:
                original.seek(0)
                _copy_file(original, candidate)
                candidate.flush()
                if _digest(candidate) != expected:
                    raise OSError("CACHE_COPY_CHECKSUM_MISMATCH")
                os.fchmod(candidate.fileno(), mode)
                os.fsync(candidate.fileno())
            os.replace(temporary, name, src_dir_fd=parent, dst_dir_fd=parent)
            os.fsync(parent)
            return True
        finally:
            try:
                os.unlink(temporary, dir_fd=parent)
            except FileNotFoundError:
                pass


def _repair_link(parent, name, target):
    try:
        if os.readlink(name, dir_fd=parent) == target:
            return False
    except OSError as error:
        if error.errno not in (errno.ENOENT, errno.EINVAL):
            raise
    temporary = ".campus-cache-" + uuid.uuid4().hex + ".tmp"
    try:
        os.symlink(target, temporary, dir_fd=parent)
        os.replace(temporary, name, src_dir_fd=parent, dst_dir_fd=parent)
        os.fsync(parent)
        return True
    finally:
        try:
            os.unlink(temporary, dir_fd=parent)
        except FileNotFoundError:
            pass


def repair_cache(source, destination):
    if os.name != "posix" or not hasattr(os, "O_NOFOLLOW"):
        raise CacheSafetyError("CACHE_REPAIR_REQUIRES_LINUX_RUNNER")
    source = Path(source).resolve(strict=True)
    destination = Path(os.path.abspath(destination))
    if source == destination or source.is_relative_to(destination) or destination.is_relative_to(source):
        raise CacheSafetyError("CACHE_SOURCE_AND_DESTINATION_MUST_BE_SEPARATE")
    directories, files, links = _inventory(source)
    root = _open_root(destination)
    repaired = 0
    try:
        os.fchmod(root, 0o777)
        # Validate every expected destination directory before copying files.
        for relative in directories:
            descriptor = _open_relative(root, relative)
            try:
                os.fchmod(descriptor, 0o777)
            finally:
                os.close(descriptor)
        for relative, mode in files:
            parent = _open_relative(root, relative.parent)
            try:
                repaired += _repair_file(source / relative, parent, relative.name, mode)
            finally:
                os.close(parent)
        for relative, target in links:
            parent = _open_relative(root, relative.parent)
            try:
                repaired += _repair_link(parent, relative.name, target)
            finally:
                os.close(parent)
        return repaired
    finally:
        os.close(root)


def main():
    if len(sys.argv) < 3 or len(sys.argv) % 2 != 1:
        raise SystemExit("Usage: cache.py SOURCE DESTINATION [SOURCE DESTINATION ...]")
    repaired = sum(repair_cache(sys.argv[index], sys.argv[index + 1]) for index in range(1, len(sys.argv), 2))
    print("Private cache entries repaired: " + str(repaired))


if __name__ == "__main__":
    main()
