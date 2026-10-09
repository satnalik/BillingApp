# Pahal Retail Windows launcher - initial release

## Run

1. Install/start PostgreSQL and create an empty database for the store. Grant its app user permission to create and update tables.
2. Copy `PahalRetail.exe` to a local folder and double-click it. Enter the PostgreSQL server, port, database, username, and password.
3. If the database has no users, the billing tables initialize and a one-time owner wizard opens. Send its installation ID to the application owner, select the signed license they issue, enter the operator provisioning key, assign the matching tenant ID, and create the first administrator.
4. The first login requires the administrator to change the temporary password. They can then manage the store and add staff.
5. Databases that already contain users skip setup and open the login page. Use the Pahal Retail tray icon to reopen the UI, show the launcher, view logs, or **Exit application**.

The bundled production React UI and Eclipse Temurin Java 17 runtime are extracted on first launch. Java, Node.js, Vite, Gradle, and source code are not required on the customer machine. Internet is not required at runtime. The server listens only on `127.0.0.1`, choosing a free port from 8765 to 8799. Double-clicking again opens the running application instead of starting another copy.

## Requirements and scope

- 64-bit Windows with .NET Framework 4.5 or later (Windows 11 includes a newer compatible Framework), a default browser, and enough local disk space for the extracted runtime and application.
- **PostgreSQL is not bundled or installed by this launcher.** Its service and database must exist. The desktop profile creates or updates billing tables when it starts; the configured database user must have permission to do so.
- A database with no users opens owner setup once. The key is entered at setup, held in memory, and never saved in the installation. After any user exists, the desktop provisioning endpoint refuses to create another first owner. Tenant administrators can add staff through the normal authenticated users endpoint.
- Swagger is disabled in the desktop profile. Development/local/QA/production profile files and the operator key are not included in the executable.
- This initial executable is unsigned. Windows may show an unknown-publisher or SmartScreen prompt. Signed distribution and customer-machine release validation are future release work.
- The wizard initializes tables in an existing PostgreSQL database. It does not install PostgreSQL or create the PostgreSQL database itself. This is not a database backup, portable PostgreSQL package, automatic updater, or final signed installer.

## Local storage and shutdown

Application packages: `%LOCALAPPDATA%\PahalRetail\packages\<package hash>\`

Logs: `%LOCALAPPDATA%\PahalRetail\logs\backend.log` and `console.log`

Database settings: `%LOCALAPPDATA%\PahalRetail\settings.dat`

Running-instance state: `%LOCALAPPDATA%\PahalRetail\running.dat`

Settings, the persistent installation signing secret, and the per-launch control token are encrypted using Windows DPAPI for the current Windows user. Credentials are passed to the JVM through its process environment, not its command line. DPAPI is not a substitute for access control against the same Windows user or an administrator.

Bills and stock stay in the configured PostgreSQL database. Replacing the executable does not replace that database. Copying the executable to another laptop does not copy customer records. A PostgreSQL backup and restore procedure is still required. Changing Windows accounts requires that account's own launcher setup; its new signing key will require a fresh login.

The launcher requests graceful backend shutdown and waits for in-flight requests and the database connection pool to finish. It does not terminate a running backend when a normal shutdown times out. An unready process started by this launcher may be stopped after failed/cancelled startup. If the launcher crashes, a subsequent launch can reconnect to its existing backend using protected running-instance state.

To change database settings, use **Show launcher > Database settings**, or launch `PahalRetail.exe --configure` after exiting the running application. Database changes restart the backend, so finish active billing work first.

Exit the application before replacing the executable. First launch of a new package extracts a separate version folder. Existing encrypted settings and the PostgreSQL data remain separate. Old package folders may be removed manually while the application is stopped; do not delete database or settings files.

## Build again

Initialize the owner signing keys and include the PUBLIC verification key at `src/main/resources/licensing/owner-public-key.der` before packaging. See [subscription licensing](../../docs/subscription-licensing.md). Existing built EXEs retain their previous behavior until explicitly rebuilt. The owner utility and private key are excluded from the customer package.

Build machine requirements: Windows, Node/npm with frontend dependencies installed, Gradle's Java 17 toolchain, and the Windows .NET Framework C# compiler. The first build downloads a redistributable Eclipse Temurin Windows x64 JRE through the [official Adoptium API](https://adoptium.net/installation/ci-scripts/) and verifies the provided SHA-256 checksum. The runtime's license and notices are preserved inside its extracted folder.

From the backend repository:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\packaging\windows\Build-Launcher.ps1
```

Optional frontend location:

```powershell
.\packaging\windows\Build-Launcher.ps1 -FrontendPath 'D:\Web Development\Billing_App_UI\pahal-billing-web'
```

Output: `dist\windows\first-store-setup\PahalRetail.exe`, SHA-256 checksum, package metadata, and this README. Only the executable is needed to launch. Build metadata pins the cached Java download for repeat builds; refresh `build\desktop-downloads\temurin-jre.json` deliberately when updating the bundled runtime.

The script builds frontend assets and the backend desktop JAR; it does not run tests or launch the application. The existing Vite environment files and normal backend build configuration are retained. The desktop profile uses relative `/api` URLs and serves React routes from the same backend process.
