# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.

## Common Commands

### Build
```bash
./gradlew build          # Compile, run tests, package plugin
./gradlew classes        # Compile only (no tests)
./gradlew runIde         # Launch a debug IDE instance with the plugin loaded
./gradlew verifyPlugin   # Check plugin compatibility against target IDE versions
```

### Test
```bash
./gradlew test           # Run all tests (JUnit 5)
./gradlew test --tests "com.lsy.idea.util.JsonUtilTest"   # Run a single test class
```

### Project Setup
- JDK 21 required (set via toolchain in `build.gradle.kts`)
- Plugin SDK targets IntelliJ Platform 2025.1 (configured in `gradle.properties` as `platformVersion`)
- `gradle.properties` controls plugin ID, name, version, since/until build ranges, and Gradle JVM args

## Architecture

### Overview

**Api2Sql** is an IntelliJ IDEA plugin that parses Spring Controller classes to batch-generate permission INSERT SQL scripts. The plugin ID is `com.lsy.idea.idea-plugin`. It supports Spring Boot 2.x/3.x with `@RestController`/`@Controller`, all `@RequestMapping` variants, and extracts context-path from `application.properties`/`.yml` files.

### Package Structure and Data Flow

```
action/     → GenerateSqlAction         Entry point: right-click action
config/     → PluginStartupActivity     DB init on project open
               PluginSettingsConfigurable Settings UI (templates + dicts)
db/         → DatabaseManager           Project-level SQLite service
               TemplateDao              Template CRUD + JSON import/export
               DictDao                  Dict item CRUD (resource/permission types)
generator/  → SqlGenerator             Template variable substitution engine
http/       → HttpCodeFetcher           HTTP GET code fetcher (Java 11 HttpClient)
model/      → InterfaceInfo, SqlTemplate, DictItem, VariableInfo, MethodTypeEnum
ui/         → MainDialog                Main three-zone dialog
               InterfaceListTable       Editable interface config table
               InterfaceTableModel      Table data model
               TemplateEditDialog       Template create/edit form
               DictManageDialog         Dict management popup
               ExtConfigPanel           Extension field key-value editor
               HttpHeadersPanel         HTTP headers key-value editor
ui/renderer/ → CodeModeCellEditor/CodeModeCellRenderer  Toggle button cell in table
util/       → SpringAnnotationUtils     PSI parser for Spring annotations
               JsonUtils                Gson-based JSON utilities
               CommUtil                 Shared helpers (variable list, HTTP test)
```

### Entry Point and Action Flow

1. User right-clicks a Controller class or method → `GenerateSqlAction.update()` checks if the PSI element is a valid Spring Controller or has a mapping annotation
2. `GenerateSqlAction.actionPerformed()` calls `SpringAnnotationUtils.extractRoutes(class)` or `extractMethodRoutes(method)` to produce `List<InterfaceInfo>`
3. A `MainDialog` (extends `DialogWrapper`, non-modal) is opened with the parsed interfaces

### PSI Parsing (`SpringAnnotationUtils`)

This is the most complex utility class (26KB). Key parsing capabilities:

- **Class-level**: Detects `@RestController`/`@Controller`, extracts `@RequestMapping` base path
- **Method-level**: Extracts paths from all mapping annotations (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`, `@RequestMapping` with `method` attribute), resolves HTTP methods including array-typed `@RequestMapping(method = {GET, POST})`
- **Annotation value resolution**: Recursively resolves literal values, constant field references (`PsiReferenceExpression` → `PsiField` initializer), string concatenation (`PsiBinaryExpression` with `+`), up to depth 8 to prevent infinite recursion
- **Interface name extraction**: Priority order: `@ApiOperation(value)` (Swagger 2) → `@Operation(summary)` (Swagger 3/OpenAPI) → Javadoc first line → empty string
- **I18n code**: Extracts from `@I18nCode` or `@I18nResourceCode` annotations (fuzzy-matched by suffix)
- **Context-path**: Reads `server.servlet.context-path` from `application.properties`/`bootstrap.properties` and `.yml`/`.yaml` files, searched upward from the source file's module root or from the project base directory

### MainDialog Three-Zone Layout

The dialog uses a vertical split layout:

1. **Top: Template Bar** — A `JComboBox` for selecting templates, with popup menus for template CRUD (new/edit/delete/export/import) and dictionary management (resource type / permission type sub-dialogs)
2. **Middle: Interface List** (`InterfaceListTable`) — An editable table where each row is a parsed interface. Columns: checkbox (select), request method + path, resource name, i18n code, resource code (with manual/HTTP mode toggle), resource type (dropdown), permission type (dropdown), remark. Dropdowns are populated from dict tables
3. **Bottom: SQL Preview** — "Generate" button triggers `SqlGenerator.generate()`, output shown in a read-only `JTextArea`, with copy-to-clipboard and export-to-file buttons (disabled when preview is empty)

### Template Switching Logic

When a template is selected in the combo box, `onTemplateSelected()` applies template defaults only to interfaces that have NOT been user-modified (`info.isUserModified() == false`):
- Sets `codeMode` and `httpCodeApi` from the template
- Interfaces with manual edits retain their resource type, permission type, resource name values
- I18n resource code is always preserved (never affected by template switching)

### SQL Generation (`SqlGenerator`)

- Template variables use `${varName}` syntax, matched by regex `\$\{([\w.]+)\}`
- Standard variables: `${resourceAddress}`, `${resourceName}`, `${i18nResourceCode}`, `${resourceCode}`, `${resourceType}`, `${permissionType}`, `${remark}`, `${creatorId}`, `${updaterId}`
- Extension variables: `${ext.xxx}` — looked up from `SqlTemplate.extConfig` Map; unknown keys produce a warning but don't block generation
- SQL text is compressed (newlines → spaces, multiple whitespace → single space) before output
- Only `selected == true` interfaces participate in generation
- Returns `GenerateResult` containing the SQL string and a list of warnings

### Database Layer

**`DatabaseManager`** is a `projectService` (one instance per project). Database file at `{project.basePath}/.idea/permission-sql/db.sqlite`. On init:
- Creates three tables with `IF NOT EXISTS`: `dict_resource_type`, `dict_permission_type`, `script_template`
- Seeds default dictionary data (resource type "接口"=1, permission types 0-4) and a default template only if tables are empty
- Runs a migration to add `http_headers` column to `script_template` (catches and ignores "column already exists" errors)
- Uses WAL journal mode and enables foreign keys

**`TemplateDao`** and **`DictDao`** are plain Java objects that take a `Connection` in their constructor — they are NOT IntelliJ services. `MainDialog` instantiates them from `DatabaseManager.getConnection()`.

### Settings Panel (`PluginSettingsConfigurable`)

Registered as `applicationConfigurable` under Settings → Tools → Api2Sql. Has two tabs:
- **Template Management**: Left-side template list + right-side detail form. Supports full CRUD, JSON export/import, HTTP code mode configuration with test button, extension fields (`ExtConfigPanel`), and HTTP headers (`HttpHeadersPanel`)
- **Dictionary Management**: Two sub-tabs for resource types and permission types, each with an editable `JBTable`

### HTTP Code Fetching (`HttpCodeFetcher`)

Uses Java 11 `HttpClient` with 5s connect timeout and 10s request timeout. Supports custom HTTP headers. Response handling:
- For normal fetch: expects JSON response with `{"code": 0, "data": "..."}` — extracts the `data` field
- For test mode (`isTest=true`): returns raw response body
- On error: throws `HttpFetchException` with descriptive Chinese error messages

### Key Model Notes

- **`InterfaceInfo.userModified`**: Tracks whether any editable field was manually changed. When `false`, template defaults are auto-applied on template switch. Set to `true` by `setResourceName()`, `setResourceType()`, and `setPermissionType()` setters
- **`InterfaceInfo.initDefaultResourceType/initDefaultPermissionType()`**: Set defaults without marking `userModified` (only applies when current value is null)
- **`SqlTemplate.extConfig`** and **`SqlTemplate.httpHeaders`**: Stored as JSON text in SQLite, deserialized to `Map<String, String>` via `JsonUtils.parseExtConfig()` on read

### Plugin Configuration (`plugin.xml`)

- Depends on `com.intellij.modules.platform` and `com.intellij.modules.java`
- `GenerateSqlAction` added to both `EditorPopupMenu` and `ProjectViewPopupMenu`
- Notification group `Api2Sql` registered (though currently using `Messages.showInfoMessage` instead of balloon notifications)

### Build Configuration Notes

- IntelliJ Platform Gradle Plugin 2.2.1
- Dependencies: `sqlite-jdbc:3.45.1.0` (with slf4j excluded, using IntelliJ's built-in), `gson:2.10.1`
- Aliyun Maven mirrors configured for faster dependency resolution in China
- `runIde` task has increased JVM heap (`-Xms512m -Xmx2048m`) for sandbox IDE debugging
