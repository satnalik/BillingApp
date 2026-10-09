using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;

[assembly: AssemblyTitle("Pahal Retail")]
[assembly: AssemblyDescription("Local Pahal Retail application launcher")]
[assembly: AssemblyCompany("Pahal Retail")]
[assembly: AssemblyProduct("Pahal Retail")]
[assembly: AssemblyVersion("0.1.0.0")]

namespace PahalRetail
{
    internal static class Program
    {
        internal static readonly string DataRoot = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "PahalRetail");
        internal static readonly string Logs = Path.Combine(DataRoot, "logs");
        internal static readonly string SettingsPath = Path.Combine(DataRoot, "settings.dat");
        internal static readonly string StatePath = Path.Combine(DataRoot, "running.dat");
        internal static readonly JavaScriptSerializer Json = new JavaScriptSerializer();

        [STAThread]
        private static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            if (!Environment.Is64BitOperatingSystem)
            {
                MessageBox.Show("This build requires 64-bit Windows.", "Pahal Retail");
                return;
            }
            try
            {
                Directory.CreateDirectory(Logs);
                string mutexName;
                using (SHA256 hash = SHA256.Create())
                    mutexName = "Local\\PahalRetail-" + BitConverter.ToString(hash.ComputeHash(
                        Encoding.UTF8.GetBytes(DataRoot.ToLowerInvariant()))).Replace("-", "");
                using (Mutex mutex = new Mutex(false, mutexName))
                {
                    bool owner;
                    try { owner = mutex.WaitOne(0); }
                    catch (AbandonedMutexException) { owner = true; }
                    if (!owner)
                    {
                        Application.Run(new ReopenForm());
                        return;
                    }
                    try
                    {
                        bool configure = Array.IndexOf(args, "--configure") >= 0;
                        Application.Run(new LauncherForm(configure));
                    }
                    finally { mutex.ReleaseMutex(); }
                }
            }
            catch (Exception error)
            {
                MessageBox.Show(error.Message, "Pahal Retail could not start",
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        internal static T ReadProtected<T>(string path) where T : class
        {
            if (!File.Exists(path)) return null;
            byte[] data = ProtectedData.Unprotect(File.ReadAllBytes(path), null, DataProtectionScope.CurrentUser);
            return Json.Deserialize<T>(Encoding.UTF8.GetString(data));
        }

        internal static void WriteProtected(string path, object value)
        {
            byte[] data = ProtectedData.Protect(Encoding.UTF8.GetBytes(Json.Serialize(value)),
                null, DataProtectionScope.CurrentUser);
            string temporary = path + ".new";
            File.WriteAllBytes(temporary, data);
            if (File.Exists(path)) File.Replace(temporary, path, null);
            else File.Move(temporary, path);
        }

        internal static string NewSecret()
        {
            byte[] bytes = new byte[48];
            using (RandomNumberGenerator rng = RandomNumberGenerator.Create()) rng.GetBytes(bytes);
            return Convert.ToBase64String(bytes);
        }

        internal static async Task<bool> IsReady(RunningState state)
        {
            if (state == null || state.Port < 8765 || state.Port > 8799 || String.IsNullOrEmpty(state.Token))
                return false;
            try
            {
                using (HttpClient client = new HttpClient(new HttpClientHandler { UseProxy = false }))
                {
                    client.Timeout = TimeSpan.FromSeconds(2);
                    client.DefaultRequestHeaders.Add("X-Desktop-Token", state.Token);
                    using (HttpResponseMessage response = await client.GetAsync(state.Url + "desktop/ready"))
                        return response.IsSuccessStatusCode &&
                            await response.Content.ReadAsStringAsync() == "pahal-retail-ready";
                }
            }
            catch (HttpRequestException) { return false; }
            catch (TaskCanceledException) { return false; }
        }

        internal static async Task<bool> IsStoreSetupRequired(RunningState state)
        {
            using (HttpClient client = new HttpClient(new HttpClientHandler { UseProxy = false }))
            {
                client.Timeout = TimeSpan.FromSeconds(4);
                client.DefaultRequestHeaders.Add("X-Desktop-Token", state.Token);
                using (HttpResponseMessage response = await client.GetAsync(state.Url + "desktop/setup/status"))
                {
                    response.EnsureSuccessStatusCode();
                    Dictionary<string, object> status = Json.Deserialize<Dictionary<string, object>>(
                        await response.Content.ReadAsStringAsync());
                    return status != null && status.ContainsKey("setupRequired") &&
                        Convert.ToBoolean(status["setupRequired"]);
                }
            }
        }

        internal static async Task<string> InstallationId(RunningState state)
        {
            using (HttpClient client = new HttpClient(new HttpClientHandler { UseProxy = false }))
            {
                client.DefaultRequestHeaders.Add("X-Desktop-Token", state.Token);
                using (HttpResponseMessage response = await client.GetAsync(state.Url + "desktop/setup/status"))
                {
                    response.EnsureSuccessStatusCode();
                    Dictionary<string, object> setup = Json.Deserialize<Dictionary<string, object>>(await response.Content.ReadAsStringAsync());
                    if (!setup.ContainsKey("signingKeyConfigured") || !Convert.ToBoolean(setup["signingKeyConfigured"]))
                        throw new InvalidOperationException("This application build has no license verification key. Ask the application owner for a configured build.");
                    return Convert.ToString(setup["deploymentId"]);
                }
            }
        }

        internal static void OpenBrowser(string url)
        {
            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
        }
    }

    public sealed class DatabaseSettings
    {
        public string Host { get; set; }
        public int Port { get; set; }
        public string Database { get; set; }
        public string User { get; set; }
        public string Password { get; set; }
        public string JwtSecret { get; set; }
    }

    public sealed class RunningState
    {
        public int Port { get; set; }
        public string Token { get; set; }
        public string Package { get; set; }
        public string Url { get { return "http://127.0.0.1:" + Port + "/"; } }
    }

    internal sealed class SetupForm : Form
    {
        private readonly TextBox host = new TextBox();
        private readonly TextBox port = new TextBox();
        private readonly TextBox database = new TextBox();
        private readonly TextBox user = new TextBox();
        private readonly TextBox password = new TextBox();
        private readonly DatabaseSettings existing;
        internal DatabaseSettings Settings { get; private set; }

        internal SetupForm(DatabaseSettings settings)
        {
            existing = settings;
            Text = "Pahal Retail - Database setup";
            Font = new Font("Segoe UI", 10);
            ClientSize = new Size(465, 385);
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            Label description = new Label { Left = 22, Top = 20, Width = 420, Height = 66,
                Text = "Connect to your PostgreSQL server and database.\r\nAn empty database receives the billing tables.\r\nA first-time setup wizard creates your store administrator." };
            Controls.Add(description);
            AddField("Server", host, 103, settings == null ? "127.0.0.1" : settings.Host);
            AddField("Port", port, 147, settings == null ? "5432" : settings.Port.ToString());
            AddField("Database", database, 191, settings == null ? "billing_app" : settings.Database);
            AddField("Username", user, 235, settings == null ? "dbadmin" : settings.User);
            AddField("Password", password, 279, settings == null ? "" : settings.Password);
            password.UseSystemPasswordChar = true;
            Button save = new Button { Text = "Save and start", Left = 260, Top = 333, Width = 180, Height = 34 };
            save.Click += Save;
            Button cancel = new Button { Text = "Cancel", Left = 144, Top = 333, Width = 105, Height = 34,
                DialogResult = DialogResult.Cancel };
            Controls.Add(save);
            Controls.Add(cancel);
            AcceptButton = save;
            CancelButton = cancel;
        }

        private void AddField(string caption, TextBox field, int top, string value)
        {
            Controls.Add(new Label { Text = caption, Left = 22, Top = top + 4, Width = 108 });
            field.SetBounds(144, top, 296, 29);
            field.Text = value ?? "";
            Controls.Add(field);
        }

        private void Save(object sender, EventArgs args)
        {
            int databasePort;
            if (Uri.CheckHostName(host.Text.Trim()) == UriHostNameType.Unknown ||
                !Int32.TryParse(port.Text.Trim(), out databasePort) || databasePort < 1 || databasePort > 65535 ||
                !Regex.IsMatch(database.Text.Trim(), "^[A-Za-z0-9_-]+$") ||
                String.IsNullOrWhiteSpace(user.Text) || String.IsNullOrEmpty(password.Text))
            {
                MessageBox.Show(this, "Enter valid server, port, database, username and password.", "Database setup");
                return;
            }
            Settings = new DatabaseSettings { Host = host.Text.Trim(), Port = databasePort,
                Database = database.Text.Trim(), User = user.Text.Trim(), Password = password.Text,
                JwtSecret = existing == null ? Program.NewSecret() : existing.JwtSecret };
            try { Program.WriteProtected(Program.SettingsPath, Settings); }
            catch (Exception error) { MessageBox.Show(this, error.Message, "Could not save settings"); return; }
            DialogResult = DialogResult.OK;
        }
    }

    internal sealed class ProvisionTenantOwnerRequest
    {
        public string userId { get; set; }
        public string password { get; set; }
        public string name { get; set; }
        public string licenseFile { get; set; }
    }

    internal sealed class OwnerSetupForm : Form
    {
        private readonly TextBox tenant = new TextBox();
        private readonly TextBox operatorKey = new TextBox();
        private readonly TextBox userId = new TextBox();
        private readonly TextBox ownerName = new TextBox();
        private readonly TextBox password = new TextBox();
        private readonly TextBox confirmation = new TextBox();
        private string licenseFile;
        private readonly Label error = new Label();
        private readonly Button create = new Button();
        private readonly Button cancel;
        private readonly Func<string, string, ProvisionTenantOwnerRequest, Task<string>> provision;

        internal OwnerSetupForm(Func<string, string, ProvisionTenantOwnerRequest, Task<string>> provision, string installationId)
        {
            this.provision = provision;
            Text = "Pahal Retail - First store setup";
            Font = new Font("Segoe UI", 10);
            ClientSize = new Size(550, 660);
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            Controls.Add(new Label { Left = 24, Top = 18, Width = 500, Height = 49,
                Text = "As the application owner, assign a tenant ID and create the store's first administrator." });
            AddField("Tenant ID", tenant, 78);
            AddField("Your application-owner key", operatorKey, 122);
            operatorKey.UseSystemPasswordChar = true;
            AddField("Administrator user ID", userId, 166);
            AddField("Administrator name", ownerName, 210);
            AddField("Temporary password", password, 254);
            AddField("Confirm password", confirmation, 298);
            password.UseSystemPasswordChar = true;
            confirmation.UseSystemPasswordChar = true;
            Controls.Add(new Label { Left = 200, Top = 336, Width = 325, Height = 39,
                ForeColor = Color.FromArgb(91, 105, 125),
                Text = "The administrator must set a new password at first login. The operator key is not saved." });
            error.SetBounds(24, 383, 500, 42);
            error.ForeColor = Color.Firebrick;
            error.AutoEllipsis = true;
            Controls.Add(error);
            cancel = new Button { Text = "Exit setup", Left = 304, Top = 457, Width = 105, Height = 34,
                DialogResult = DialogResult.Cancel };
            Controls.Add(cancel);
            create.SetBounds(418, 457, 108, 34);
            create.Text = "Create store";
            create.Click += CreateOwner;
            Controls.Add(create);
            Controls.Add(new Label { Left = 24, Top = 500, Width = 500, Height = 24, Text = "Installation ID - send this to the application owner:" });
            Controls.Add(new TextBox { Left = 24, Top = 527, Width = 500, ReadOnly = true, Text = installationId });
            Button selectLicense = new Button { Left = 24, Top = 566, Width = 180, Height = 34, Text = "Select signed license" };
            Label licenseName = new Label { Left = 215, Top = 571, Width = 310, Height = 55, Text = "License required before creating the first administrator." };
            selectLicense.Click += delegate {
                using (OpenFileDialog dialog = new OpenFileDialog { Filter = "Pahal license|*.pahal-license;*.lic;*.json|All files|*.*" })
                {
                    if (dialog.ShowDialog(this) != DialogResult.OK) return;
                    try {
                        if (new FileInfo(dialog.FileName).Length > 65536) throw new InvalidOperationException("License file must be smaller than 64 KB.");
                        licenseFile = File.ReadAllText(dialog.FileName, Encoding.UTF8);
                        licenseName.Text = Path.GetFileName(dialog.FileName);
                    } catch (Exception exception) { MessageBox.Show(this, exception.Message, "License import"); }
                }
            };
            Controls.Add(selectLicense); Controls.Add(licenseName);
            AcceptButton = create;
            CancelButton = cancel;
        }

        private void AddField(string caption, TextBox input, int top)
        {
            Controls.Add(new Label { Text = caption, Left = 24, Top = top + 5, Width = 180 });
            input.SetBounds(210, top, 316, 29);
            Controls.Add(input);
        }

        private async void CreateOwner(object sender, EventArgs args)
        {
            string storeId = tenant.Text.Trim();
            string key = operatorKey.Text;
            string id = userId.Text.Trim();
            string name = ownerName.Text.Trim();
            string initialPassword = password.Text;
            if (String.IsNullOrWhiteSpace(licenseFile)) { error.Text = "Select the signed license issued for this tenant and installation."; return; }
            if (!Regex.IsMatch(storeId, "^[A-Za-z0-9_-]{1,80}$") ||
                key.Length < 32 || String.IsNullOrWhiteSpace(id) || String.IsNullOrWhiteSpace(name) ||
                initialPassword.Length < 12 || initialPassword != confirmation.Text)
            {
                error.Text = "Enter a tenant ID and owner key, administrator ID and name, and matching passwords of at least 12 characters.";
                return;
            }

            create.Enabled = false;
            error.Text = "Creating the first administrator...";
            try
            {
                string failure = await provision(storeId, key,
                    new ProvisionTenantOwnerRequest { userId = id, name = name, password = initialPassword, licenseFile = licenseFile });
                if (String.IsNullOrEmpty(failure))
                {
                    operatorKey.Clear();
                    password.Clear();
                    confirmation.Clear();
                    DialogResult = DialogResult.OK;
                    return;
                }
                error.Text = failure;
            }
            catch (Exception exception) { error.Text = exception.Message; }
            finally { if (!IsDisposed) create.Enabled = true; }
        }
    }

    internal sealed class ReopenForm : Form
    {
        internal ReopenForm()
        {
            Text = "Pahal Retail";
            ClientSize = new Size(360, 105);
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            Controls.Add(new Label { Text = "Opening the running application...", Left = 20, Top = 32,
                Width = 325, Font = new Font("Segoe UI", 11) });
            Shown += async delegate
            {
                try
                {
                    Stopwatch wait = Stopwatch.StartNew();
                    while (!IsDisposed && wait.Elapsed < TimeSpan.FromSeconds(150))
                    {
                        RunningState state = Program.ReadProtected<RunningState>(Program.StatePath);
                        if (await Program.IsReady(state))
                        {
                            if (await Program.IsStoreSetupRequired(state))
                            {
                                if (!IsDisposed) MessageBox.Show(this,
                                    "Finish the first-store setup in the open Pahal Retail window, then log in.", "Store setup is in progress");
                                return;
                            }
                            if (!IsDisposed) Program.OpenBrowser(state.Url);
                            return;
                        }
                        await Task.Delay(700);
                    }
                    if (!IsDisposed) MessageBox.Show(this,
                        "Pahal Retail is already running or starting. Check its tray icon and startup window.", "Pahal Retail");
                }
                catch (Exception error) { if (!IsDisposed) MessageBox.Show(this, error.Message, "Pahal Retail"); }
                finally { if (!IsDisposed) Close(); }
            };
        }
    }

    internal sealed class LauncherForm : Form
    {
        private readonly Label status = new Label();
        private readonly Button open = new Button();
        private readonly Button settingsButton = new Button();
        private readonly NotifyIcon tray;
        private readonly CancellationTokenSource cancellation = new CancellationTokenSource();
        private readonly bool configure;
        private readonly object logLock = new object();
        private Process backend;
        private RunningState state;
        private DatabaseSettings databaseSettings;
        private string activeOperatorKey;
        private bool ready;
        private bool closing;
        private bool allowClose;
        private Task startup;

        internal LauncherForm(bool configure)
        {
            this.configure = configure;
            Text = "Pahal Retail";
            Font = new Font("Segoe UI", 10);
            ClientSize = new Size(470, 195);
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath) ?? SystemIcons.Application;
            Controls.Add(new Label { Text = "Pahal Retail", Left = 23, Top = 20, Width = 400, Height = 32,
                Font = new Font("Segoe UI", 17, FontStyle.Bold), ForeColor = Color.FromArgb(19, 42, 77) });
            status.SetBounds(25, 65, 420, 62);
            status.Text = "Starting your store application...";
            Controls.Add(status);
            open.SetBounds(25, 143, 105, 32);
            open.Text = "Open login";
            open.Enabled = false;
            open.Click += delegate { TryOpen(); };
            settingsButton.SetBounds(140, 143, 140, 32);
            settingsButton.Text = "Database settings";
            settingsButton.Enabled = false;
            settingsButton.Click += async delegate { await ChangeSettings(); };
            Button logs = new Button { Left = 290, Top = 143, Width = 70, Height = 32, Text = "Logs" };
            logs.Click += delegate { Process.Start("explorer.exe", Program.Logs); };
            Button exit = new Button { Left = 370, Top = 143, Width = 70, Height = 32, Text = "Exit" };
            exit.Click += delegate { Close(); };
            Controls.AddRange(new Control[] { open, settingsButton, logs, exit });
            ContextMenuStrip menu = new ContextMenuStrip();
            menu.Items.Add("Open application", null, delegate { TryOpen(); });
            menu.Items.Add("Show launcher", null, delegate { Show(); WindowState = FormWindowState.Normal; Activate(); });
            menu.Items.Add("Open logs", null, delegate { Process.Start("explorer.exe", Program.Logs); });
            menu.Items.Add("Exit application", null, delegate { Close(); });
            tray = new NotifyIcon { Text = "Pahal Retail - starting", Icon = Icon, ContextMenuStrip = menu, Visible = true };
            tray.DoubleClick += delegate { TryOpen(); };
            Shown += delegate { if (startup == null) startup = StartApplication(configure); };
            Resize += delegate { if (WindowState == FormWindowState.Minimized && ready) Hide(); };
            FormClosing += OnClosing;
        }

        private async Task StartApplication(bool showSetup)
        {
            settingsButton.Enabled = false;
            try
            {
                state = Program.ReadProtected<RunningState>(Program.StatePath);
                if (await Program.IsReady(state))
                {
                    // Recover management of a backend left running after a launcher crash.
                    if (showSetup) throw new InvalidOperationException("Exit the running application before changing database settings.");
                    databaseSettings = Program.ReadProtected<DatabaseSettings>(Program.SettingsPath);
                    ready = true;
                    if (await SetupFirstStore()) { MarkReady(); }
                    else Close();
                    return;
                }
                DatabaseSettings settings = Program.ReadProtected<DatabaseSettings>(Program.SettingsPath);
                if (settings == null || showSetup)
                {
                    using (SetupForm setup = new SetupForm(settings))
                    {
                        if (setup.ShowDialog(this) != DialogResult.OK) { Close(); return; }
                        settings = setup.Settings;
                    }
                }
                databaseSettings = settings;
                cancellation.Token.ThrowIfCancellationRequested();
                status.Text = "Preparing the included Java runtime...\r\nFirst launch can take a little longer.";
                string payload = await Task.Run(() => ExtractPayload(cancellation.Token));
                cancellation.Token.ThrowIfCancellationRequested();
                state = new RunningState { Port = FindPort(), Token = Program.NewSecret(), Package = Path.GetFileName(payload) };
                StartBackend(settings, state, null);
                status.Text = "Starting the application and connecting to PostgreSQL...";
                await WaitForBackend();
                if (!await SetupFirstStore()) { Close(); return; }
                MarkReady();
            }
            catch (OperationCanceledException) { }
            catch (Exception error)
            {
                StopUnreadyBackend();
                status.Text = error.Message;
                tray.Text = "Pahal Retail - startup failed";
                if (!closing) MessageBox.Show(this, error.Message + "\r\n\r\nLogs: " + Program.Logs,
                    "Pahal Retail could not start", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
            finally { if (!closing) settingsButton.Enabled = true; }
        }

        private async Task<bool> SetupFirstStore()
        {
            if (!await Program.IsStoreSetupRequired(state)) return true;
            status.Text = "Preparing the new store. Enter your owner details in the setup window.";
            using (OwnerSetupForm ownerSetup = new OwnerSetupForm(ProvisionFirstOwnerAsync, await Program.InstallationId(state)))
            {
                return ownerSetup.ShowDialog(this) == DialogResult.OK;
            }
        }

        private void StartBackend(DatabaseSettings settings, RunningState launchState, string operatorKey)
        {
            string payload = Path.Combine(Program.DataRoot, "packages", launchState.Package);
            string java = Path.Combine(payload, "runtime", "bin", "java.exe");
            string jar = Path.Combine(payload, "app", "pahal-retail-desktop.jar");
            ProcessStartInfo start = new ProcessStartInfo(java, "-Xms64m -Xmx512m -jar \"" + jar + "\"") {
                UseShellExecute = false, CreateNoWindow = true, WorkingDirectory = Program.DataRoot,
                RedirectStandardOutput = true, RedirectStandardError = true };
            string[] remove = { "SPRING_APPLICATION_JSON", "SPRING_CONFIG_LOCATION", "SPRING_CONFIG_ADDITIONAL_LOCATION",
                "APP_PROVISIONING_KEY", "APP_JWT_SECRET", "APP_CORS_ALLOWED_ORIGINS", "SERVER_PORT", "SERVER_ADDRESS",
                "SPRING_JPA_HIBERNATE_DDL_AUTO", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS" };
            foreach (string variable in remove) start.EnvironmentVariables.Remove(variable);
            start.EnvironmentVariables["SPRING_PROFILES_ACTIVE"] = "desktop";
            start.EnvironmentVariables["SPRING_CONFIG_LOCATION"] = "classpath:/";
            string host = settings.Host.Contains(":") ? "[" + settings.Host + "]" : settings.Host;
            start.EnvironmentVariables["SPRING_DATASOURCE_URL"] = "jdbc:postgresql://" + host + ":" + settings.Port + "/" + settings.Database;
            start.EnvironmentVariables["SPRING_DATASOURCE_USERNAME"] = settings.User;
            start.EnvironmentVariables["SPRING_DATASOURCE_PASSWORD"] = settings.Password;
            start.EnvironmentVariables["JWT_SECRET"] = settings.JwtSecret;
            start.EnvironmentVariables["APP_PROVISIONING_KEY"] = operatorKey ?? "";
            start.EnvironmentVariables["PAHAL_CONTROL_TOKEN"] = launchState.Token;
            start.EnvironmentVariables["PAHAL_PORT"] = launchState.Port.ToString();
            start.EnvironmentVariables["PAHAL_LOG_FILE"] = Path.Combine(Program.Logs, "backend.log").Replace('\\', '/');
            Program.WriteProtected(Program.StatePath, launchState);
            backend = new Process { StartInfo = start };
            backend.OutputDataReceived += LogOutput;
            backend.ErrorDataReceived += LogOutput;
            string consoleLog = Path.Combine(Program.Logs, "console.log");
            if (File.Exists(consoleLog) && new FileInfo(consoleLog).Length > 10 * 1024 * 1024)
                File.WriteAllText(consoleLog, "");
            backend.Start();
            // Do not retain the operator key in the launcher process configuration after spawning Java.
            start.EnvironmentVariables["APP_PROVISIONING_KEY"] = "";
            backend.BeginOutputReadLine();
            backend.BeginErrorReadLine();
        }

        private async Task WaitForBackend()
        {
            status.Text = "Starting the application and preparing the billing database...";
            Stopwatch timer = Stopwatch.StartNew();
            while (timer.Elapsed < TimeSpan.FromSeconds(150))
            {
                cancellation.Token.ThrowIfCancellationRequested();
                if (backend == null || backend.HasExited)
                    throw new InvalidOperationException("The backend could not start. Check that PostgreSQL is running and that this database user can create or update billing tables. See Logs for details.");
                if (await Program.IsReady(state)) { ready = true; return; }
                await Task.Delay(500, cancellation.Token);
            }
            throw new TimeoutException("The application took too long to start. Check PostgreSQL and the backend log.");
        }

        private async Task<string> ProvisionFirstOwnerAsync(string tenantId, string operatorKey, ProvisionTenantOwnerRequest owner)
        {
            if (!String.Equals(activeOperatorKey, operatorKey, StringComparison.Ordinal))
            {
                try
                {
                    if (ready) await StopReadyBackend();
                    ready = false;
                    if (backend != null) { backend.Dispose(); backend = null; }
                    state = new RunningState { Port = state.Port, Token = Program.NewSecret(), Package = state.Package };
                    status.Text = "Applying the operator authorization for first-store setup...";
                    StartBackend(databaseSettings, state, operatorKey);
                    await WaitForBackend();
                    activeOperatorKey = operatorKey;
                }
                catch (Exception exception) { return exception.Message; }
            }

            try
            {
                using (HttpClient client = new HttpClient(new HttpClientHandler { UseProxy = false }))
                {
                    client.Timeout = TimeSpan.FromSeconds(20);
                    client.DefaultRequestHeaders.Add("X-Provisioning-Key", operatorKey);
                    string address = state.Url + "api/provisioning/tenants/" + Uri.EscapeDataString(tenantId) + "/owner";
                    using (HttpResponseMessage response = await client.PostAsync(address,
                        new StringContent(Program.Json.Serialize(owner), Encoding.UTF8, "application/json")))
                    {
                        if (response.StatusCode == HttpStatusCode.Created)
                        {
                            activeOperatorKey = null;
                            return null;
                        }
                        string body = await response.Content.ReadAsStringAsync();
                        try
                        {
                            Dictionary<string, object> error = Program.Json.Deserialize<Dictionary<string, object>>(body);
                            if (error != null && error.ContainsKey("message")) body = Convert.ToString(error["message"]);
                        }
                        catch (Exception) { }
                        return response.StatusCode == HttpStatusCode.Unauthorized
                            ? "The operator key was not accepted. Check it and try again."
                            : String.IsNullOrWhiteSpace(body) ? "Owner setup failed (HTTP " + (int)response.StatusCode + ")." : body;
                    }
                }
            }
            catch (Exception exception) { return "Could not provision the first administrator: " + exception.Message; }
        }

        private void MarkReady()
        {
            ready = true;
            open.Enabled = true;
            status.Text = "Application running at " + state.Url + "\r\nUse the tray icon to reopen or exit.";
            tray.Text = "Pahal Retail - running";
            TryOpen();
            Hide();
        }

        private void TryOpen()
        {
            if (!ready) { Show(); Activate(); return; }
            try { Program.OpenBrowser(state.Url); }
            catch (Exception error) { Show(); MessageBox.Show(this, error.Message, "Could not open browser"); }
        }

        private async Task ChangeSettings()
        {
            settingsButton.Enabled = false;
            try
            {
                await StopReadyBackend();
                ready = false;
                open.Enabled = false;
                startup = StartApplication(true);
                await startup;
            }
            catch (Exception error) { MessageBox.Show(this, error.Message, "Database settings"); }
            finally { settingsButton.Enabled = true; }
        }

        private async void OnClosing(object sender, FormClosingEventArgs args)
        {
            if (allowClose) return;
            args.Cancel = true;
            if (closing) return;
            closing = true;
            cancellation.Cancel();
            status.Text = "Stopping the application safely...";
            try
            {
                if (startup != null && !startup.IsCompleted) await startup;
                if (ready) await StopReadyBackend();
                else StopUnreadyBackend();
                tray.Visible = false;
                tray.Dispose();
                if (backend != null) backend.Dispose();
                allowClose = true;
                Close();
            }
            catch (Exception error)
            {
                closing = false;
                Show();
                MessageBox.Show(this, error.Message, "Application is still running");
            }
        }

        private async Task StopReadyBackend()
        {
            if (state == null) return;
            if (!await Program.IsReady(state))
            {
                if (backend != null && !backend.HasExited)
                    throw new InvalidOperationException("The backend is not responding. It has been left running to protect ongoing work. Check the logs before exiting.");
                return;
            }
            using (HttpClient client = new HttpClient(new HttpClientHandler { UseProxy = false }))
            {
                client.Timeout = TimeSpan.FromSeconds(10);
                client.DefaultRequestHeaders.Add("X-Desktop-Token", state.Token);
                using (HttpResponseMessage response = await client.PostAsync(state.Url + "desktop/shutdown", new StringContent("")))
                    response.EnsureSuccessStatusCode();
            }
            Stopwatch wait = Stopwatch.StartNew();
            while (wait.Elapsed < TimeSpan.FromSeconds(45))
            {
                if (backend != null ? backend.HasExited : !PortIsOccupied(state.Port)) return;
                await Task.Delay(300);
            }
            throw new TimeoutException("The backend is still finishing requests. It has been left running; try Exit again shortly.");
        }

        private void StopUnreadyBackend()
        {
            // Only abort our own failed startup process, never a server that reached readiness.
            if (!ready && backend != null && !backend.HasExited)
            {
                backend.Kill();
                backend.WaitForExit(5000);
            }
        }

        private void LogOutput(object sender, DataReceivedEventArgs args)
        {
            if (args.Data == null) return;
            try { lock (logLock) File.AppendAllText(Path.Combine(Program.Logs, "console.log"), args.Data + Environment.NewLine); }
            catch (IOException) { }
        }

        private static int FindPort()
        {
            for (int port = 8765; port <= 8799; port++) if (!PortIsOccupied(port)) return port;
            throw new InvalidOperationException("No application port is available between 8765 and 8799.");
        }

        private static bool PortIsOccupied(int port)
        {
            TcpListener listener = new TcpListener(IPAddress.Loopback, port);
            try { listener.Start(); return false; }
            catch (SocketException) { return true; }
            finally { listener.Stop(); }
        }

        private static string ExtractPayload(CancellationToken cancellation)
        {
            Assembly assembly = Assembly.GetExecutingAssembly();
            string hash;
            using (StreamReader reader = new StreamReader(assembly.GetManifestResourceStream("Pahal.PayloadHash")))
                hash = reader.ReadToEnd().Trim();
            if (!Regex.IsMatch(hash, "^[a-f0-9]{64}$")) throw new InvalidDataException("Invalid application package identity.");
            string cache = Path.Combine(Program.DataRoot, "packages");
            string target = Path.Combine(cache, hash);
            if (File.Exists(Path.Combine(target, ".complete")) &&
                File.Exists(Path.Combine(target, "runtime", "bin", "java.exe")) &&
                File.Exists(Path.Combine(target, "app", "pahal-retail-desktop.jar"))) return target;
            if (Directory.Exists(target))
                throw new IOException("An incomplete application package exists at " + target + ". Rename that package folder and launch again.");
            Directory.CreateDirectory(cache);
            string staging = Path.Combine(cache, ".extract-" + Guid.NewGuid().ToString("N"));
            Directory.CreateDirectory(staging);
            using (Stream source = assembly.GetManifestResourceStream("Pahal.Payload"))
            using (SHA256 checksum = SHA256.Create())
            {
                string actual = BitConverter.ToString(checksum.ComputeHash(source)).Replace("-", "").ToLowerInvariant();
                if (actual != hash) throw new InvalidDataException("The application package is damaged. Download it again.");
            }
            using (Stream source = assembly.GetManifestResourceStream("Pahal.Payload"))
            using (ZipArchive zip = new ZipArchive(source, ZipArchiveMode.Read))
            {
                string root = Path.GetFullPath(staging) + Path.DirectorySeparatorChar;
                foreach (ZipArchiveEntry entry in zip.Entries)
                {
                    cancellation.ThrowIfCancellationRequested();
                    string destination = Path.GetFullPath(Path.Combine(staging, entry.FullName));
                    if (!destination.StartsWith(root, StringComparison.OrdinalIgnoreCase))
                        throw new InvalidDataException("An application package path is invalid.");
                    if (String.IsNullOrEmpty(entry.Name)) { Directory.CreateDirectory(destination); continue; }
                    Directory.CreateDirectory(Path.GetDirectoryName(destination));
                    using (Stream input = entry.Open())
                    using (FileStream output = File.Create(destination)) input.CopyTo(output);
                }
            }
            cancellation.ThrowIfCancellationRequested();
            File.WriteAllText(Path.Combine(staging, ".complete"), hash);
            // Both resolved paths are direct children of the application cache; user data is separate.
            Directory.Move(staging, target);
            return target;
        }
    }
}
