# ULogin
Ultimate Login. A lightweight login system for both premium and non-premium users.

Commands - USER:
- "/register <password>" - If a user joins the server for the first time then they must run this command to begin moving/run commands. This creates a new line in a file with their password and username (encrypted of course)
- "/login <password>" - After the first join and account creation, the user must run this command to log back in, they are unable to move/operate any other commands until they run this command.

Commands - ADMIN (ulogin.admin):
- "/unregister <username>" - Unregisters the user so they have to register again. Should be used if the user forgets their password.

Permissions:
ulogin.admin:
  description: Allows access to admin login commands.
  default: op
