# Moonraker Macro Extraction Recommendation

## Summary

The recommended approach is to treat Moonraker as a bridge into Klipper's live printer object model, not as a separate macro registry. Moonraker does not maintain a dedicated macro database. Klipper exposes each `[gcode_macro ...]` section as a printer object named:

```text
gcode_macro MACRO_NAME
```

A robust macro-discovery pipeline should:

1. Discover macro objects through `/printer/objects/list`.
2. Query live macro state through `/printer/objects/query`.
3. Query `configfile` to get raw macro config, descriptions, G-code bodies, and configured variables.
4. Use `/printer/gcode/help` only as supplemental help text or a fallback description source.
5. Infer callable macro parameters by parsing the macro's raw G-code template.

Macro parameters are not formally declared by Klipper. They are dynamically accessed inside Jinja2 templates through `params`, so any parameter extraction is heuristic.

---

## Recommended Extraction Pipeline

### 1. Discover Available Macro Objects

Use:

```http
GET /printer/objects/list
```

Example response:

```json
{
  "result": {
    "objects": [
      "webhooks",
      "configfile",
      "gcode_macro PRINT_START",
      "gcode_macro _SECONDARY_HOME",
      "gcode_macro BED_MESH_LOAD",
      "extruder"
    ]
  }
}
```

Filter object names with:

```js
const PREFIX = "gcode_macro ";

const macroObjects = objects.filter(name => name.startsWith(PREFIX));
const macros = macroObjects.map(objectName => ({
  objectName,
  name: objectName.slice(PREFIX.length),
}));
```

Prefer `startsWith("gcode_macro ")` over a loose substring check. A substring search could accidentally match unrelated object names.

---

### 2. Preserve Hidden/Internal Macros, But Mark Them

Macros whose names begin with `_` are commonly treated by frontends such as Mainsail and Fluidd as helper or internal macros.

Do not discard them from your canonical model. Instead, mark them:

```js
const hiddenByConvention = macro.name.startsWith("_");
```

Recommended behavior:

```ts
type MacroVisibility = {
  hiddenByConvention: boolean;
  visibleInDefaultUi: boolean;
};
```

Suggested interpretation:

```js
visibleInDefaultUi = !hiddenByConvention;
```

This lets your command-line UI support both user-facing macro lists and advanced/internal macro inspection.

---

### 3. Query Live Macro State Variables

Use `/printer/objects/query` to query the live status of each macro object.

Batch all macro objects in one request:

```http
POST /printer/objects/query
Content-Type: application/json
```

```json
{
  "objects": {
    "gcode_macro PRINT_START": null,
    "gcode_macro CANCEL_PRINT": null,
    "gcode_macro _HELPER": null
  }
}
```

Example response:

```json
{
  "result": {
    "status": {
      "gcode_macro PRINT_START": {
        "layer_count": 0,
        "retract_length": 0.8,
        "verbose_output": true
      },
      "gcode_macro CANCEL_PRINT": {},
      "gcode_macro _HELPER": {
        "some_state": "ready"
      }
    },
    "eventtime": 12345.67
  }
}
```

These values correspond to macro variables created with config entries like:

```ini
[gcode_macro PRINT_START]
variable_layer_count: 0
variable_retract_length: 0.8
variable_verbose_output: True
gcode:
  ...
```

In the live object state, Klipper exposes them without the `variable_` prefix:

```json
{
  "layer_count": 0,
  "retract_length": 0.8,
  "verbose_output": true
}
```

---

### 4. Subscribe to Live Macro Variable Changes

For reactive UIs, subscribe over Moonraker's websocket API using `printer.objects.subscribe`.

Example websocket request:

```json
{
  "jsonrpc": "2.0",
  "method": "printer.objects.subscribe",
  "params": {
    "objects": {
      "gcode_macro PRINT_START": null,
      "gcode_macro _HELPER": null
    }
  },
  "id": 1
}
```

Use this when the CLI needs to react to changes in macro variables after commands such as:

```gcode
SET_GCODE_VARIABLE MACRO=PRINT_START VARIABLE=layer_count VALUE=10
```

---

### 5. Query Raw Macro Configuration

To infer descriptions, raw G-code, configured variables, and macro behavior, query the `configfile` object.

Prefer requesting only the needed fields because `configfile` can be large:

```http
POST /printer/objects/query
Content-Type: application/json
```

```json
{
  "objects": {
    "configfile": ["config", "settings"]
  }
}
```

Example response shape:

```json
{
  "result": {
    "status": {
      "configfile": {
        "config": {
          "gcode_macro PRINT_START": {
            "description": "Starts the print",
            "variable_layer_count": "0",
            "variable_retract_length": "0.8",
            "gcode": "{% set BED_TEMP = params.BED|default(60)|float %}\n{% set EXTRUDER_TEMP = params.EXTRUDER|default(190)|float %}\nM140 S{BED_TEMP}\nM104 S{EXTRUDER_TEMP}\nG28"
          }
        },
        "settings": {
          "gcode_macro print_start": {
            "description": "Starts the print",
            "variable_layer_count": 0,
            "variable_retract_length": 0.8,
            "gcode": "{% set BED_TEMP = params.BED|default(60)|float %}\n{% set EXTRUDER_TEMP = params.EXTRUDER|default(190)|float %}\nM140 S{BED_TEMP}\nM104 S{EXTRUDER_TEMP}\nG28"
          }
        }
      }
    },
    "eventtime": 12347.89
  }
}
```

Recommended use:

- `configfile.config`: closer to the raw config as written.
- `configfile.settings`: parsed/normalized settings where available.
- Prefer `settings` for typed configured variables when present.
- Fall back to `config` for exact section names and raw strings.

Normalize macro names internally because Klipper macro names are effectively case-insensitive.

Example normalizer:

```js
function normalizeMacroName(name) {
  return name.trim().toUpperCase();
}
```

---

### 6. Use `/printer/gcode/help` as Supplemental Help Text

Use:

```http
GET /printer/gcode/help
```

Example response:

```json
{
  "result": {
    "G28": "Home one or more axes",
    "PRINT_START": "Heats up the bed and nozzle, runs bed mesh, and primes extruder.",
    "BED_MESH_LOAD": "Loads default mesh configuration profile.",
    "SET_PRESSURE_ADVANCE": "Set pressure advance parameters"
  }
}
```

For macros, map the macro name against keys in the help dictionary.

Recommended priority for descriptions:

1. `configfile.settings[section].description`
2. `configfile.config[section].description`
3. `/printer/gcode/help[MACRO_NAME]`
4. Empty or generated fallback

Do not rely on `/printer/gcode/help` as the canonical macro list or canonical macro metadata source. It is useful, but not guaranteed to be exhaustive for every command or macro.

---

## Inferring Macro Parameters

### Important Limitation

Klipper does not maintain a formal schema of expected macro parameters.

A macro can access parameters dynamically inside its Jinja2 template:

```jinja2
{% set bed = params.BED|default(60)|float %}
{% set extruder = params.EXTRUDER|default(190)|float %}
```

The callable parameters `BED` and `EXTRUDER` are not declared elsewhere as a clean API schema. They must be inferred from the macro's `gcode` body and/or from human-written descriptions.

---

### Basic Parameter Extraction

A simple starting regex is:

```regex
params\.([A-Za-z0-9_]+)
```

However, a better first-pass extractor should support dot syntax and bracket syntax:

```js
const dotParam = /\bparams\s*\.\s*([A-Za-z_][A-Za-z0-9_]*)\b/g;
const bracketParam = /\bparams\s*\[\s*['"]([A-Za-z_][A-Za-z0-9_]*)['"]\s*\]/g;

function extractParams(gcode) {
  const found = new Set();

  for (const re of [dotParam, bracketParam]) {
    let match;
    while ((match = re.exec(gcode)) !== null) {
      found.add(match[1].toUpperCase());
    }
  }

  return [...found].sort();
}
```

Example:

```js
const gcode = `
{% set BED_TEMP = params.BED|default(60)|float %}
{% set EXTRUDER_TEMP = params["EXTRUDER"]|default(190)|float %}
M140 S{BED_TEMP}
M104 S{EXTRUDER_TEMP}
G28
`;

console.log(extractParams(gcode));
// ["BED", "EXTRUDER"]
```

---

### Extract Defaults

To detect common default values:

```js
const defaultPattern = /\bparams\s*\.\s*([A-Za-z_][A-Za-z0-9_]*)\s*\|\s*default\(([^)]*)\)/g;
```

Example parser:

```js
function extractParamDefaults(gcode) {
  const defaults = new Map();
  let match;

  while ((match = defaultPattern.exec(gcode)) !== null) {
    defaults.set(match[1].toUpperCase(), match[2].trim());
  }

  return defaults;
}
```

Input:

```jinja2
{% set BED_TEMP = params.BED|default(60)|float %}
{% set EXTRUDER_TEMP = params.EXTRUDER|default(190)|float %}
```

Output:

```json
{
  "BED": "60",
  "EXTRUDER": "190"
}
```

---

### Extract Type Hints

Common type filters include:

```text
|int
|float
|string
|lower
|upper
```

Possible extraction pattern:

```js
const paramWithFilterPattern = /\bparams\s*\.\s*([A-Za-z_][A-Za-z0-9_]*)([^%}\n]*)/g;
const filterPattern = /\|\s*(int|float|string|lower|upper|bool)\b/g;

function extractParamTypeHints(gcode) {
  const hints = new Map();
  let match;

  while ((match = paramWithFilterPattern.exec(gcode)) !== null) {
    const name = match[1].toUpperCase();
    const tail = match[2];
    let filterMatch;

    while ((filterMatch = filterPattern.exec(tail)) !== null) {
      const filter = filterMatch[1];

      if (filter === "int") hints.set(name, "int");
      else if (filter === "float") hints.set(name, "float");
      else if (filter === "string") hints.set(name, "string");
      else if (filter === "bool") hints.set(name, "bool");
    }
  }

  return hints;
}
```

Type inference should be treated as a UI hint, not as a guarantee.

---

## Macro Interaction Points Worth Extracting

Beyond user-callable parameters, a CLI can expose richer interaction points.

### 1. Runtime Variables Exposed by the Macro Object

Source:

```text
/printer/objects/query -> gcode_macro MACRO_NAME
```

These are live values.

Example:

```json
{
  "layer_count": 0,
  "verbose_output": true
}
```

Use for:

- status display
- live dashboards
- subscriptions
- debug output

---

### 2. Configured Macro Variables

Source:

```text
configfile.config["gcode_macro MACRO_NAME"].variable_*
configfile.settings["gcode_macro macro_name"].variable_*
```

Example config:

```ini
[gcode_macro PRINT_START]
variable_layer_count: 0
variable_verbose_output: True
gcode:
  ...
```

Recommended extraction:

```js
function extractConfiguredVariables(section) {
  const vars = {};

  for (const [key, value] of Object.entries(section)) {
    if (key.startsWith("variable_")) {
      vars[key.slice("variable_".length)] = value;
    }
  }

  return vars;
}
```

---

### 3. Parameters Read from `params`

Source:

```text
configfile.config/settings -> macro gcode string
```

Examples:

```jinja2
params.BED
params.EXTRUDER
params.CHAMBER|default(0)|int
params["PROFILE"]
```

Use for:

- command-line prompts
- generated command builders
- shell completion
- validation warnings

---

### 4. Raw Parameters

Klipper macros can use `rawparams`, which represents the full unparsed parameter string.

Example:

```jinja2
RESPOND MSG="raw args: {rawparams}"
```

If a macro uses `rawparams`, your CLI probably cannot infer a clean parameter schema. Mark it as accepting raw arguments.

Suggested detection:

```js
const usesRawParams = /\brawparams\b/.test(gcode);
```

---

### 5. Printer Objects Referenced by the Macro

Macros can read printer state through the `printer` object.

Examples:

```jinja2
printer.extruder.temperature
printer.heater_bed.target
printer["gcode_macro PRINT_START"].layer_count
```

Useful extraction patterns:

```js
const printerDotObject = /\bprinter\.([A-Za-z_][A-Za-z0-9_]*)\b/g;
const printerBracketObject = /\bprinter\s*\[\s*['"]([^'"]+)['"]\s*\]/g;
```

Use this only as a best-effort dependency map. Jinja2 expressions can be too dynamic for regex to perfectly analyze.

---

### 6. Variables Written by the Macro

Macros can write to macro variables with `SET_GCODE_VARIABLE`.

Example:

```gcode
SET_GCODE_VARIABLE MACRO=PRINT_START VARIABLE=layer_count VALUE=10
```

Basic extraction pattern:

```js
const setGcodeVariablePattern = /\bSET_GCODE_VARIABLE\b[^\n]*/gi;
```

For each matched line, parse fields like:

```text
MACRO=PRINT_START
VARIABLE=layer_count
VALUE=10
```

This lets your CLI show side effects such as:

```text
writes variable PRINT_START.layer_count
```

---

## Recommended Data Model

```ts
type MacroInfo = {
  name: string;
  objectName: string;
  normalizedName: string;

  hiddenByConvention: boolean;
  visibleInDefaultUi: boolean;

  description?: string;
  gcode?: string;
  renameExisting?: string;

  stateVariables: Record<string, unknown>;
  configuredVariables: Record<string, unknown>;

  inferredParams: MacroParamInfo[];

  usesRawParams: boolean;
  referencedPrinterObjects: string[];
  writtenMacroVariables: WrittenMacroVariable[];
};

type MacroParamInfo = {
  name: string;
  default?: string;
  typeHint?: "int" | "float" | "string" | "bool" | "unknown";
  required: boolean | "unknown";
  confidence: "high" | "medium" | "low";
};

type WrittenMacroVariable = {
  macro?: string;
  variable?: string;
  valueExpression?: string;
  rawLine: string;
};
```

Suggested parameter confidence levels:

```text
high   = direct params.NAME or params["NAME"] usage found
medium = parameter mentioned in description/help text only
low    = inferred from rawparams, comments, or complex forwarding logic
```

Suggested `required` heuristic:

```text
required = true       if params.NAME appears without a nearby default(...)
required = false      if params.NAME is piped through default(...)
required = "unknown" if extracted from docs or complex logic
```

---

## Suggested CLI Commands

Possible command-line UI affordances:

```text
moon macros list
moon macros list --all
moon macros show PRINT_START
moon macros params PRINT_START
moon macros vars PRINT_START
moon macros deps PRINT_START
moon macros call PRINT_START BED=60 EXTRUDER=210
moon macros watch PRINT_START
```

Example `moon macros list` output:

```text
NAME             HIDDEN  DESCRIPTION
PRINT_START      no      Starts the print
BED_MESH_LOAD    no      Loads default mesh configuration profile
_SECONDARY_HOME  yes     Internal helper macro
```

Example `moon macros params PRINT_START` output:

```text
PARAM      TYPE   DEFAULT  REQUIRED  CONFIDENCE
BED        float  60       no        high
EXTRUDER   float  190      no        high
CHAMBER    int    0        no        high
```

Example `moon macros show PRINT_START` output:

```text
Macro: PRINT_START
Object: gcode_macro PRINT_START
Hidden: no
Description: Starts the print

Configured variables:
  layer_count = 0
  retract_length = 0.8

Runtime variables:
  layer_count = 0
  retract_length = 0.8
  verbose_output = true

Inferred parameters:
  BED      float default=60
  EXTRUDER float default=190

References:
  printer.extruder
  printer.heater_bed

Writes:
  PRINT_START.layer_count
```

---

## Practical Caveats

Parameter extraction cannot be perfect because macro templates are dynamic.

Cases that may defeat regex-based extraction:

```jinja2
{% set key = "BED" %}
{% set value = params[key] %}
```

```jinja2
{% for name in ["BED", "EXTRUDER"] %}
  RESPOND MSG="{params[name]}"
{% endfor %}
```

```jinja2
OTHER_MACRO {rawparams}
```

```jinja2
{% set p = params %}
{% set bed = p.BED|default(60)|float %}
```

Recommended handling:

- Show inferred parameters as inferred, not guaranteed.
- Expose raw macro G-code for advanced users.
- Allow users to override or annotate parameter schemas in your CLI config.
- Treat `/printer/gcode/help` and macro `description` as human-authored hints.
- Preserve hidden macros in `--all` views.

---

## Bottom Line

The best approach is:

```text
objects/list
  -> discover gcode_macro objects

objects/query for gcode_macro objects
  -> get live macro variables

objects/query for configfile.config/settings
  -> get descriptions, raw gcode, configured variables, rename_existing

gcode/help
  -> supplement descriptions/help text

regex/Jinja-aware parsing of gcode
  -> infer params, defaults, type hints, rawparams usage, printer object dependencies, and SET_GCODE_VARIABLE writes
```

Treat macro parameters as inferred interaction points rather than API-declared schema fields.

---

## Reference Links

- Moonraker External API: Printer Endpoints  
  https://moonraker.readthedocs.io/en/latest/external_api/printer/

- Moonraker API Introduction  
  https://moonraker.readthedocs.io/en/latest/external_api/introduction/

- Moonraker Printer Objects  
  https://moonraker.readthedocs.io/en/latest/printer_objects/

- Klipper Command Templates  
  https://www.klipper3d.org/Command_Templates.html

- Klipper Config Reference: `gcode_macro`  
  https://www.klipper3d.org/Config_Reference.html#gcode_macro

- Fluidd Macros Documentation  
  https://docs.fluidd.xyz/features/macros/

- Mainsail Macros Documentation  
  https://docs.mainsail.xyz/settings/macros/
