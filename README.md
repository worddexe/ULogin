# ULogin
Ultimate Login. A lightweight login system for both premium and non-premium users.

Commands - USER:
- "/register <password>" - If a user joins the server for the first time then they must run this command to begin moving/run commands. This creates a new line in a file with their password and username (encrypted of course)
- "/login <password>" - After the first join and account creation, the user must run this command to log back in, they are unable to move/operate any other commands until they run this command.
- "/autologin" - If a user is premium they can run this command to automatically log them in. Plugin does checks to see if they are premium or not so that cracked players cannot change their username to someone elses.

Commands - ADMIN (ulogin.admin):
- "/resetpassword <username>" - Removes the users password from their account so that they have to /register again. Arguably more secure than "/changepassword".
- "/changepassword <username> <new password>" - Changes the users password to the one the administrator enters.
- "/autologindisable <username>" - Disables autologin for a user incase they are cracked, turned it on and now can't login, or if a premium player becomes cracked.

Permissions:
ulogin.admin:
  description: Allows access to admin login commands.
  default: op
