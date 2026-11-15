import { readFileSync, readdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { roleCodes, roleProfiles } from './fixtures/navigation'

// Checked-in fresh-default SQL contract; this is not a database/runtime execution claim.
// Changed grant syntax fails explicitly instead of silently guessing fixture permissions.
function checkedInBackendGrants() {
  const db = fileURLToPath(new URL('../../backend/resource-service/src/main/resources/db/', import.meta.url))
  const seed = readFileSync(join(db, 'seed/R__identity_defaults.sql'), 'utf8')
  const grants = Object.fromEntries(roleCodes.map(role => [role, new Set<string>()])) as Record<typeof roleCodes[number], Set<string>>
  const catalog = new Set<string>()
  const register = (sql: string) => {
    for (const match of sql.matchAll(/\(\s*'([^']+)'\s*,\s*'[^']*'\s*,\s*'[^']*'\s*\)/g)) catalog.add(match[1]!)
  }
  const quoted = (value: string) => [...value.matchAll(/'([^']+)'/g)].map(match => match[1]!)
  register(seed)
  const initial = seed.match(/WHERE code='([^']+)' OR \(role_code='SYSTEM_ADMIN' AND code IN \(([^)]+)\)\)/)
  if (!initial) throw new Error('Review changed backend default-role seed before updating navigation fixtures')
  for (const role of roleCodes) grants[role].add(initial[1]!)
  for (const code of quoted(initial[2]!)) grants.SYSTEM_ADMIN.add(code)
  const migration = join(db, 'modules/identity/migration')
  const files = readdirSync(migration).filter(name => /^V\d+__.*\.sql$/.test(name))
    .sort((a, b) => Number(a.match(/^V(\d+)/)?.[1]) - Number(b.match(/^V(\d+)/)?.[1]))
  for (const file of files) {
    const sql = readFileSync(join(migration, file), 'utf8')
    register(sql)
    for (const match of sql.matchAll(/INSERT INTO iam\.role_permissions[\s\S]*?;/g)) {
      const statement = match[0]
      const role = statement.match(/WHERE r\.code\s*(?:=\s*'([^']+)'|IN\s*\(([^)]*)\))/)
      if (!role) throw new Error('Unsupported role grant in ' + file)
      const roles = role[1] ? [role[1]] : quoted(role[2]!)
      const filter = statement.match(/p\.code\s*(?:=\s*'([^']+)'|IN\s*\(([^)]*)\))/)
      const permissions = statement.includes('CROSS JOIN iam.permissions') ? [...catalog]
        : filter?.[1] ? [filter[1]] : filter?.[2] ? quoted(filter[2]) : null
      if (!permissions) throw new Error('Unsupported permission grant in ' + file)
      for (const target of roles) {
        if (!(target in grants)) throw new Error('Review new default role ' + target)
        for (const permission of permissions) {
          if (!catalog.has(permission)) throw new Error('Unregistered permission ' + permission)
          grants[target as typeof roleCodes[number]].add(permission)
        }
      }
    }
  }
  return grants
}

describe('fresh backend default permission fixtures', () => {
  it.each(roleCodes)('keeps %s test profile aligned with the checked-in IAM grants', role => {
    const expected = checkedInBackendGrants()[role]
    expect([...roleProfiles[role].permissions].sort()).toEqual([...expected].sort())
    expect(roleProfiles[role].permissions.length).toBe(new Set(roleProfiles[role].permissions).size)
  })
})
