import zhCN from './zh-CN'

export default {
  "app": {
    "title": "Fudan Study Room",
    "name": "Campus study rooms",
    "headingLine1": "Find your place.",
    "headingLine2": "Make time to focus.",
    "description": "Explore campus study spaces and plan your next study session."
  },
  "common": {
    "language": "Language",
    "home": "Back to home",
    "homeLabel": "FduStudyRoom home",
    "retry": "Try again",
    "checkInput": "Check input"
  },
  "errors": {
    "unknown": "The action could not be completed. Please try again later.",
    "network": "Could not connect. Check your network and try again.",
    "timeout": "The request timed out. Please try again.",
    "cancelled": "The request was cancelled.",
    "invalidResponse": "The service returned an unexpected response. Please try again later.",
    "invalidRequest": "The request is invalid. Check the input and try again.",
    "http": "The request failed. Please try again later.",
    "unauthenticated": "Your session has expired. Please sign in again.",
    "forbidden": "You do not have permission to perform this action.",
    "notFound": "The requested content could not be found.",
    "conflict": "The data has changed. Refresh and try again.",
    "validation": "Some information is invalid. Please check your input.",
    "rateLimited": "Too many requests. Please wait and try again.",
    "server": "The service is temporarily unavailable. Please try again later."
  },
  "preview": {
    "skip": "Skip to components",
    "eyebrow": "Shared styles and components",
    "title": "Clear, consistent interactions.",
    "description": "Check the appearance and behavior of buttons, inputs and feedback. All actions apply only to this preview.",
    "categories": "Component categories",
    "actions": {
      "title": "Buttons and status",
      "description": "Clear action hierarchy, loading and disabled states.",
      "interactive": "Interactive preview",
      "primary": "Primary action",
      "secondary": "Secondary action",
      "ghost": "Text action",
      "danger": "Danger action",
      "small": "Small button",
      "medium": "Medium button",
      "large": "Large button",
      "loading": "Show loading",
      "disabled": "Disabled",
      "statusLabel": "Status tag examples"
    },
    "feedback": {
      "initial": "Select a button to see its feedback.",
      "primary": "Primary action selected.",
      "secondary": "Secondary action selected.",
      "ghost": "Text action selected.",
      "danger": "Danger style preview. Nothing was deleted.",
      "small": "Small button selected.",
      "medium": "Medium button selected.",
      "large": "Large button selected.",
      "loading": "Loading demo in progress. Repeated clicks are disabled.",
      "done": "Loading demo complete."
    },
    "status": {
      "neutral": "Default",
      "info": "Info",
      "success": "Complete",
      "warning": "Pending",
      "error": "Failed"
    },
    "form": {
      "title": "Form inputs",
      "description": "Labels, help and errors are linked to their inputs.",
      "label": "Display name",
      "placeholder": "What should we call you?",
      "hint": "Try checking an empty field, then enter a name.",
      "required": "Please enter a display name.",
      "disabledValue": "This field cannot be edited",
      "disabledLabel": "Disabled input",
      "disabledHint": "Disabled fields do not accept input.",
      "success": "Input check passed",
      "successDescription": "This only updates the preview. Nothing is saved to a server."
    },
    "messages": {
      "title": "Loading and feedback",
      "description": "Explain what is happening and provide a next step.",
      "loading": "Loading preview content…",
      "infoTitle": "Information",
      "infoDescription": "Keep guidance brief without interrupting the current action.",
      "warningTitle": "Please note",
      "warningDescription": "Use clear text for status. Color provides additional context.",
      "errorTitle": "Loading error example",
      "errorDescription": "You can try again. This example makes no network requests.",
      "successTitle": "Retry demo complete",
      "successDescription": "Success uses the same message structure.",
      "restore": "Restore error example"
    },
    "visual": {
      "title": "A shared visual foundation",
      "description": "Colors, corners and spacing come from shared styles.",
      "primary": "Primary",
      "soft": "Soft background",
      "success": "Success",
      "warning": "Warning",
      "error": "Error",
      "footer": "Inputs and buttons share consistent sizing. Component text comes from the Chinese and English dictionaries."
    },
    "footer": "Development preview only · No authentication or reservation service"
  },
  "auth": {
    "card": {
      "title": "Sign in"
    },
    "official": {
      "action": "Sign in with Fudan UIS",
      "unavailable": "University sign-in is unavailable"
    },
    "development": {
      "badge": "Development mode",
      "label": "Development account",
      "action": "Sign in",
      "accounts": {
        "student": "Development student",
        "admin": "Development administrator",
        "maintainer": "Development maintainer"
      }
    },
    "actions": {
      "recheck": "Check again",
      "signingIn": "Signing in…",
      "signOut": "Sign out"
    },
    "loading": "Checking your sign-in status…",
    "session": {
      "role": "Role",
      "identity": "Identity",
      "account": "Account",
      "student": "Student",
      "staff": "Staff"
    },
    "roles": {
      "student": "Student user",
      "admin": "System administrator",
      "maintainer": "Maintainer"
    },
    "errors": {
      "title": "The action could not be completed",
      "csrf": "The security check expired. Please try again.",
      "ineligible": "This identity is not eligible. Ask an administrator to verify your access.",
      "disabled": "This account or role is disabled. Please contact an administrator.",
      "environment": "Sign-in is unavailable with this configuration. Please contact an administrator.",
      "unconfirmed": "Your sign-in could not be confirmed. Check again before retrying."
    }
  },
  "shell": {
    "navigation": {
      "workspace": "Workspace",
      "users": "User management"
    },
    "navigationLabel": "Main navigation",
    "account": "My account",
    "name": "Name",
    "skip": "Skip to content",
    "empty": "No content yet",
    "pending": "Not available yet",
    "forbidden": "Access denied",
    "notFound": "Page not found",
    "returnWorkspace": "Back to workspace"
  }
} satisfies typeof zhCN
