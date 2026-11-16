export default {
  "title": "Rooms",
  "manage": "Manage rooms",
  "actions": {
    "addRoom": "Add room",
    "editRoom": "Edit room",
    "addSeat": "Add seat",
    "editSeat": "Edit seat",
    "save": "Save",
    "cancel": "Cancel",
    "retry": "Retry",
    "reload": "Reload",
    "query": "Search",
    "reserve": "Reserve",
    "request": "Room request",
    "repair": "Report a fault",
    "code": "Today's check-in code",
    "refresh": "Refresh",
    "back": "Back to rooms"
  },
  "fields": {
    "room": "Room",
    "roomCode": "Room code",
    "roomName": "Room name",
    "campus": "Campus",
    "building": "Building",
    "capacity": "Capacity",
    "department": "Department restriction (optional)",
    "opens": "Opening time",
    "closes": "Closing time",
    "seatCode": "Seat code",
    "socket": "Power outlet",
    "state": "Status",
    "actions": "Actions",
    "starts": "Start time",
    "ends": "End time"
  },
  "status": {
    "enabled": "Enabled",
    "disabled": "Disabled",
    "bookingEnabled": "Booking enabled",
    "bookingDisabled": "Booking disabled",
    "public": "Public room",
    "department": "Department: {code}",
    "allDay": "All day",
    "loading": "Loading",
    "saving": "Saving",
    "noRooms": "No rooms",
    "noSeats": "No seats",
    "noAvailable": "No available seats",
    "all": "All",
    "withSocket": "With outlet",
    "withoutSocket": "Without outlet",
    "shanghai": "Shanghai time"
  },
  "sockets": {
    "NONE": "None",
    "FIXED": "Fixed",
    "RAIL": "Rail"
  },
  "errors": {
    "load": "Unable to load",
    "save": "Unable to save",
    "changed": "This record has changed. Reload it.",
    "capacity": "Capacity does not match enabled seats",
    "disabled": "This room is disabled",
    "time": "Choose a valid start and end time"
  },
  "availability": "Availability",
  "code": {
    "title": "Today's check-in code",
    "date": "Date",
    "unavailable": "Check-in code is unavailable"
  }
} as const
