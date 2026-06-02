# Klipper G-Code Command Catalog

**Purpose:** Sendable Klipper G-Code commands from the official G-Codes reference. Config-section options are excluded unless they expose a command.

## Sources

- https://www.klipper3d.org/Command_Templates.html
- https://www.klipper3d.org/G-Codes.html

## Full-detail entries

| ID | Name | Tier | Runtime | Availability | Upstream |
|---|---|---|---|---|---|
| `KGC-G1` | `G0 / G1` | full | reference_only | `object_present: toolhead` | https://www.klipper3d.org/G-Codes.html |
| `KGC-G1_JOG` | `G1 relative jog` | full | registered | `object_present: toolhead` | https://www.klipper3d.org/G-Codes.html |
| `KGC-G1_OVERRIDE_JOG` | `SET_KINEMATIC_POSITION + G1` | full | registered | `gcode_command_present: FORCE_MOVE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-G1_EXTRUDE` | `G1 E` | full | registered | `object_present: extruder` | https://www.klipper3d.org/G-Codes.html |
| `KGC-G28` | `G28` | full | reference_only | `object_present: toolhead` | https://www.klipper3d.org/G-Codes.html |
| `KGC-G28_HOME_ALL` | `G28` | full | registered | `object_present: toolhead` | https://www.klipper3d.org/G-Codes.html |
| `KGC-G28_HOME_XY` | `G28 X Y` | full | registered | `object_present: toolhead` | https://www.klipper3d.org/G-Codes.html |
| `KGC-G28_HOME_AXIS` | `G28 <axis>` | full | registered | `object_present: toolhead` | https://www.klipper3d.org/G-Codes.html |
| `KGC-M84` | `M18 / M84` | full | reference_only | `object_present: toolhead` | https://www.klipper3d.org/G-Codes.html |
| `KGC-M84_DISABLE_STEPPERS` | `M84` | full | registered | `object_present: toolhead` | https://www.klipper3d.org/G-Codes.html |
| `KGC-M83` | `M82 / M83` | full | reference_only | `object_present: extruder` | https://www.klipper3d.org/G-Codes.html |
| `KGC-G91` | `G91` | full | reference_only | `object_present: toolhead` | https://www.klipper3d.org/G-Codes.html |
| `KGC-T_SELECT_TOOL` | `T<n>` | full | registered | `object_present: extruder` | https://www.klipper3d.org/G-Codes.html |
| `KGC-BED_MESH_CALIBRATE` | `BED_MESH_CALIBRATE` | full | planned_v1 | `object_present: bed_mesh` | https://www.klipper3d.org/G-Codes.html |
| `KGC-BED_MESH_OUTPUT` | `BED_MESH_OUTPUT` | full | planned_v1 | `object_present: bed_mesh` | https://www.klipper3d.org/G-Codes.html |
| `KGC-BED_MESH_MAP` | `BED_MESH_MAP` | full | planned_v1 | `object_present: bed_mesh` | https://www.klipper3d.org/G-Codes.html |
| `KGC-BED_MESH_CLEAR` | `BED_MESH_CLEAR` | full | planned_v1 | `object_present: bed_mesh` | https://www.klipper3d.org/G-Codes.html |
| `KGC-BED_MESH_PROFILE` | `BED_MESH_PROFILE` | full | planned_v1 | `object_present: bed_mesh` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SAVE_CONFIG` | `SAVE_CONFIG` | full | planned_v1 | `object_present: configfile` | https://www.klipper3d.org/G-Codes.html |
| `KGC-FORCE_MOVE` | `FORCE_MOVE` | full | registered | `gcode_command_present: FORCE_MOVE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_KINEMATIC_POSITION` | `SET_KINEMATIC_POSITION` | full | reference_only | `gcode_command_present: SET_KINEMATIC_POSITION` | https://www.klipper3d.org/G-Codes.html |
| `KGC-RESTART` | `RESTART` | full | reference_only | `always` | https://www.klipper3d.org/G-Codes.html |
| `KGC-FIRMWARE_RESTART` | `FIRMWARE_RESTART` | full | reference_only | `always` | https://www.klipper3d.org/G-Codes.html |
| `KGC-STATUS` | `STATUS` | full | reference_only | `always` | https://www.klipper3d.org/G-Codes.html |
| `KGC-HELP` | `HELP` | full | reference_only | `always` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_GCODE_VARIABLE` | `SET_GCODE_VARIABLE` | full | planned_v1 | `always` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SAVE_GCODE_STATE` | `SAVE_GCODE_STATE` | full | reference_only | `always` | https://www.klipper3d.org/G-Codes.html |
| `KGC-RESTORE_GCODE_STATE` | `RESTORE_GCODE_STATE` | full | reference_only | `always` | https://www.klipper3d.org/G-Codes.html |
| `KGC-TURN_OFF_HEATERS` | `TURN_OFF_HEATERS` | full | registered | `any_object_present` | https://www.klipper3d.org/G-Codes.html |
| `KGC-TEMPERATURE_WAIT` | `TEMPERATURE_WAIT` | full | reference_only | `any_object_present` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_HEATER_TEMPERATURE` | `SET_HEATER_TEMPERATURE` | full | registered | `any_object_present` | https://www.klipper3d.org/G-Codes.html |
| `KGC-PAUSE` | `PAUSE` | full | planned_v1 | `object_present: pause_resume` | https://www.klipper3d.org/G-Codes.html |
| `KGC-RESUME` | `RESUME` | full | planned_v1 | `object_present: pause_resume` | https://www.klipper3d.org/G-Codes.html |
| `KGC-CLEAR_PAUSE` | `CLEAR_PAUSE` | full | planned_v1 | `object_present: pause_resume` | https://www.klipper3d.org/G-Codes.html |
| `KGC-CANCEL_PRINT` | `CANCEL_PRINT` | full | planned_v1 | `object_present: pause_resume` | https://www.klipper3d.org/G-Codes.html |
| `KGC-PROBE` | `PROBE` | full | planned_v1 | `object_present: probe` | https://www.klipper3d.org/G-Codes.html |
| `KGC-QUERY_PROBE` | `QUERY_PROBE` | full | planned_v1 | `object_present: probe` | https://www.klipper3d.org/G-Codes.html |
| `KGC-PROBE_ACCURACY` | `PROBE_ACCURACY` | full | planned_v1 | `object_present: probe` | https://www.klipper3d.org/G-Codes.html |
| `KGC-PROBE_CALIBRATE` | `PROBE_CALIBRATE` | full | planned_v1 | `object_present: probe` | https://www.klipper3d.org/G-Codes.html |
| `KGC-QUAD_GANTRY_LEVEL` | `QUAD_GANTRY_LEVEL` | full | planned_v1 | `object_present: quad_gantry_level` | https://www.klipper3d.org/G-Codes.html |
| `KGC-RESPOND` | `RESPOND` | full | planned_v1 | `always` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SCREWS_TILT_CALCULATE` | `SCREWS_TILT_CALCULATE` | full | planned_v1 | `object_present: screws_tilt_adjust` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SDCARD_PRINT_FILE` | `SDCARD_PRINT_FILE` | full | planned_v1 | `object_present: virtual_sdcard` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SDCARD_RESET_FILE` | `SDCARD_RESET_FILE` | full | planned_v1 | `object_present: virtual_sdcard` | https://www.klipper3d.org/G-Codes.html |
| `KGC-Z_TILT_ADJUST` | `Z_TILT_ADJUST` | full | planned_v1 | `object_present: z_tilt` | https://www.klipper3d.org/G-Codes.html |
| `KGC-LOAD_FILAMENT` | `LOAD_FILAMENT` | full | registered | `macro_present: LOAD_FILAMENT` | https://www.klipper3d.org/Command_Templates.html |
| `KGC-UNLOAD_FILAMENT` | `UNLOAD_FILAMENT` | full | registered | `macro_present: UNLOAD_FILAMENT` | https://www.klipper3d.org/Command_Templates.html |
| `KGC-SET_HEATER_TEMPERATURE_PRESET` | `SET_HEATER_TEMPERATURE preset pair` | full | registered | `object_present: heater_bed` | https://www.klipper3d.org/G-Codes.html |

## Light/reference entries

| ID | Name | Tier | Runtime | Availability | Upstream |
|---|---|---|---|---|---|
| `KGC-G90` | `G90` | light | reference_only | `object_present: toolhead` | https://www.klipper3d.org/G-Codes.html |
| `KGC-M104` | `M104` | light | reference_only | `object_present: extruder` | https://www.klipper3d.org/G-Codes.html |
| `KGC-M109` | `M109` | light | reference_only | `object_present: extruder` | https://www.klipper3d.org/G-Codes.html |
| `KGC-M140` | `M140` | light | reference_only | `object_present: heater_bed` | https://www.klipper3d.org/G-Codes.html |
| `KGC-M190` | `M190` | light | reference_only | `object_present: heater_bed` | https://www.klipper3d.org/G-Codes.html |
| `KGC-M112` | `M112` | light | reference_only | `always` | https://www.klipper3d.org/G-Codes.html |
| `KGC-M204` | `M204` | light | reference_only | `object_present: toolhead` | https://www.klipper3d.org/G-Codes.html |
| `KGC-M220` | `M220` | light | reference_only | `object_present: gcode_move` | https://www.klipper3d.org/G-Codes.html |
| `KGC-M221` | `M221` | light | reference_only | `object_present: gcode_move` | https://www.klipper3d.org/G-Codes.html |
| `KGC-ACCELEROMETER_MEASURE` | `ACCELEROMETER_MEASURE` | light | reference_only | `gcode_command_present: ACCELEROMETER_MEASURE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-ACCELEROMETER_QUERY` | `ACCELEROMETER_QUERY` | light | reference_only | `gcode_command_present: ACCELEROMETER_QUERY` | https://www.klipper3d.org/G-Codes.html |
| `KGC-ACCELEROMETER_DEBUG_READ` | `ACCELEROMETER_DEBUG_READ` | light | reference_only | `gcode_command_present: ACCELEROMETER_DEBUG_READ` | https://www.klipper3d.org/G-Codes.html |
| `KGC-ACCELEROMETER_DEBUG_WRITE` | `ACCELEROMETER_DEBUG_WRITE` | light | reference_only | `gcode_command_present: ACCELEROMETER_DEBUG_WRITE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-ANGLE_CALIBRATE` | `ANGLE_CALIBRATE` | light | reference_only | `gcode_command_present: ANGLE_CALIBRATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-ANGLE_CHIP_CALIBRATE` | `ANGLE_CHIP_CALIBRATE` | light | reference_only | `gcode_command_present: ANGLE_CHIP_CALIBRATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-ANGLE_DEBUG_READ` | `ANGLE_DEBUG_READ` | light | reference_only | `gcode_command_present: ANGLE_DEBUG_READ` | https://www.klipper3d.org/G-Codes.html |
| `KGC-ANGLE_DEBUG_WRITE` | `ANGLE_DEBUG_WRITE` | light | reference_only | `gcode_command_present: ANGLE_DEBUG_WRITE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-AXIS_TWIST_COMPENSATION_CALIBRATE` | `AXIS_TWIST_COMPENSATION_CALIBRATE` | light | reference_only | `gcode_command_present: AXIS_TWIST_COMPENSATION_CALIBRATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-BED_MESH_OFFSET` | `BED_MESH_OFFSET` | light | reference_only | `gcode_command_present: BED_MESH_OFFSET` | https://www.klipper3d.org/G-Codes.html |
| `KGC-BED_SCREWS_ADJUST` | `BED_SCREWS_ADJUST` | light | reference_only | `gcode_command_present: BED_SCREWS_ADJUST` | https://www.klipper3d.org/G-Codes.html |
| `KGC-BED_TILT_CALIBRATE` | `BED_TILT_CALIBRATE` | light | reference_only | `gcode_command_present: BED_TILT_CALIBRATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-BLTOUCH_DEBUG` | `BLTOUCH_DEBUG` | light | reference_only | `gcode_command_present: BLTOUCH_DEBUG` | https://www.klipper3d.org/G-Codes.html |
| `KGC-BLTOUCH_STORE` | `BLTOUCH_STORE` | light | reference_only | `gcode_command_present: BLTOUCH_STORE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-UPDATE_DELAYED_GCODE` | `UPDATE_DELAYED_GCODE` | light | reference_only | `gcode_command_present: UPDATE_DELAYED_GCODE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-DELTA_CALIBRATE` | `DELTA_CALIBRATE` | light | reference_only | `gcode_command_present: DELTA_CALIBRATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-DELTA_ANALYZE` | `DELTA_ANALYZE` | light | reference_only | `gcode_command_present: DELTA_ANALYZE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_DISPLAY_GROUP` | `SET_DISPLAY_GROUP` | light | reference_only | `gcode_command_present: SET_DISPLAY_GROUP` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_DUAL_CARRIAGE` | `SET_DUAL_CARRIAGE` | light | reference_only | `gcode_command_present: SET_DUAL_CARRIAGE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SAVE_DUAL_CARRIAGE_STATE` | `SAVE_DUAL_CARRIAGE_STATE` | light | reference_only | `gcode_command_present: SAVE_DUAL_CARRIAGE_STATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-RESTORE_DUAL_CARRIAGE_STATE` | `RESTORE_DUAL_CARRIAGE_STATE` | light | reference_only | `gcode_command_present: RESTORE_DUAL_CARRIAGE_STATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-ENDSTOP_PHASE_CALIBRATE` | `ENDSTOP_PHASE_CALIBRATE` | light | reference_only | `gcode_command_present: ENDSTOP_PHASE_CALIBRATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-EXCLUDE_OBJECT` | `EXCLUDE_OBJECT` | light | reference_only | `gcode_command_present: EXCLUDE_OBJECT` | https://www.klipper3d.org/G-Codes.html |
| `KGC-EXCLUDE_OBJECT_DEFINE` | `EXCLUDE_OBJECT_DEFINE` | light | reference_only | `gcode_command_present: EXCLUDE_OBJECT_DEFINE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-EXCLUDE_OBJECT_START` | `EXCLUDE_OBJECT_START` | light | reference_only | `gcode_command_present: EXCLUDE_OBJECT_START` | https://www.klipper3d.org/G-Codes.html |
| `KGC-EXCLUDE_OBJECT_END` | `EXCLUDE_OBJECT_END` | light | reference_only | `gcode_command_present: EXCLUDE_OBJECT_END` | https://www.klipper3d.org/G-Codes.html |
| `KGC-ACTIVATE_EXTRUDER` | `ACTIVATE_EXTRUDER` | light | reference_only | `gcode_command_present: ACTIVATE_EXTRUDER` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_PRESSURE_ADVANCE` | `SET_PRESSURE_ADVANCE` | light | reference_only | `gcode_command_present: SET_PRESSURE_ADVANCE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_EXTRUDER_ROTATION_DISTANCE` | `SET_EXTRUDER_ROTATION_DISTANCE` | light | reference_only | `gcode_command_present: SET_EXTRUDER_ROTATION_DISTANCE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SYNC_EXTRUDER_MOTION` | `SYNC_EXTRUDER_MOTION` | light | reference_only | `gcode_command_present: SYNC_EXTRUDER_MOTION` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_FAN_SPEED` | `SET_FAN_SPEED` | light | reference_only | `gcode_command_present: SET_FAN_SPEED` | https://www.klipper3d.org/G-Codes.html |
| `KGC-QUERY_FILAMENT_SENSOR` | `QUERY_FILAMENT_SENSOR` | light | reference_only | `gcode_command_present: QUERY_FILAMENT_SENSOR` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_FILAMENT_SENSOR` | `SET_FILAMENT_SENSOR` | light | reference_only | `gcode_command_present: SET_FILAMENT_SENSOR` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_RETRACTION` | `SET_RETRACTION` | light | reference_only | `gcode_command_present: SET_RETRACTION` | https://www.klipper3d.org/G-Codes.html |
| `KGC-GET_RETRACTION` | `GET_RETRACTION` | light | reference_only | `gcode_command_present: GET_RETRACTION` | https://www.klipper3d.org/G-Codes.html |
| `KGC-STEPPER_BUZZ` | `STEPPER_BUZZ` | light | reference_only | `gcode_command_present: STEPPER_BUZZ` | https://www.klipper3d.org/G-Codes.html |
| `KGC-GET_POSITION` | `GET_POSITION` | light | reference_only | `gcode_command_present: GET_POSITION` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_GCODE_OFFSET` | `SET_GCODE_OFFSET` | light | reference_only | `gcode_command_present: SET_GCODE_OFFSET` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_STEPPER_CARRIAGES` | `SET_STEPPER_CARRIAGES` | light | reference_only | `gcode_command_present: SET_STEPPER_CARRIAGES` | https://www.klipper3d.org/G-Codes.html |
| `KGC-QUERY_FILAMENT_WIDTH` | `QUERY_FILAMENT_WIDTH` | light | reference_only | `gcode_command_present: QUERY_FILAMENT_WIDTH` | https://www.klipper3d.org/G-Codes.html |
| `KGC-RESET_FILAMENT_WIDTH_SENSOR` | `RESET_FILAMENT_WIDTH_SENSOR` | light | reference_only | `gcode_command_present: RESET_FILAMENT_WIDTH_SENSOR` | https://www.klipper3d.org/G-Codes.html |
| `KGC-DISABLE_FILAMENT_WIDTH_SENSOR` | `DISABLE_FILAMENT_WIDTH_SENSOR` | light | reference_only | `gcode_command_present: DISABLE_FILAMENT_WIDTH_SENSOR` | https://www.klipper3d.org/G-Codes.html |
| `KGC-ENABLE_FILAMENT_WIDTH_SENSOR` | `ENABLE_FILAMENT_WIDTH_SENSOR` | light | reference_only | `gcode_command_present: ENABLE_FILAMENT_WIDTH_SENSOR` | https://www.klipper3d.org/G-Codes.html |
| `KGC-QUERY_RAW_FILAMENT_WIDTH` | `QUERY_RAW_FILAMENT_WIDTH` | light | reference_only | `gcode_command_present: QUERY_RAW_FILAMENT_WIDTH` | https://www.klipper3d.org/G-Codes.html |
| `KGC-ENABLE_FILAMENT_WIDTH_LOG` | `ENABLE_FILAMENT_WIDTH_LOG` | light | reference_only | `gcode_command_present: ENABLE_FILAMENT_WIDTH_LOG` | https://www.klipper3d.org/G-Codes.html |
| `KGC-DISABLE_FILAMENT_WIDTH_LOG` | `DISABLE_FILAMENT_WIDTH_LOG` | light | reference_only | `gcode_command_present: DISABLE_FILAMENT_WIDTH_LOG` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_IDLE_TIMEOUT` | `SET_IDLE_TIMEOUT` | light | reference_only | `gcode_command_present: SET_IDLE_TIMEOUT` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_INPUT_SHAPER` | `SET_INPUT_SHAPER` | light | reference_only | `gcode_command_present: SET_INPUT_SHAPER` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_LED` | `SET_LED` | light | reference_only | `gcode_command_present: SET_LED` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_LED_TEMPLATE` | `SET_LED_TEMPLATE` | light | reference_only | `gcode_command_present: SET_LED_TEMPLATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-LOAD_CELL_DIAGNOSTIC` | `LOAD_CELL_DIAGNOSTIC` | light | reference_only | `gcode_command_present: LOAD_CELL_DIAGNOSTIC` | https://www.klipper3d.org/G-Codes.html |
| `KGC-LOAD_CELL_CALIBRATE` | `LOAD_CELL_CALIBRATE` | light | reference_only | `gcode_command_present: LOAD_CELL_CALIBRATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-LOAD_CELL_TARE` | `LOAD_CELL_TARE` | light | reference_only | `gcode_command_present: LOAD_CELL_TARE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-LOAD_CELL_READ_LOAD_CELL_NAME` | `LOAD_CELL_READ load_cell="name"` | light | reference_only | `gcode_command_present: LOAD_CELL_READ_LOAD_CELL_NAME` | https://www.klipper3d.org/G-Codes.html |
| `KGC-LOAD_CELL_TEST_TAP` | `LOAD_CELL_TEST_TAP` | light | reference_only | `gcode_command_present: LOAD_CELL_TEST_TAP` | https://www.klipper3d.org/G-Codes.html |
| `KGC-MANUAL_PROBE` | `MANUAL_PROBE` | light | reference_only | `gcode_command_present: MANUAL_PROBE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-Z_ENDSTOP_CALIBRATE` | `Z_ENDSTOP_CALIBRATE` | light | reference_only | `gcode_command_present: Z_ENDSTOP_CALIBRATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-Z_OFFSET_APPLY_ENDSTOP` | `Z_OFFSET_APPLY_ENDSTOP` | light | reference_only | `gcode_command_present: Z_OFFSET_APPLY_ENDSTOP` | https://www.klipper3d.org/G-Codes.html |
| `KGC-MANUAL_STEPPER` | `MANUAL_STEPPER` | light | reference_only | `gcode_command_present: MANUAL_STEPPER` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_DIGIPOT` | `SET_DIGIPOT` | light | reference_only | `gcode_command_present: SET_DIGIPOT` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_PIN` | `SET_PIN` | light | reference_only | `gcode_command_present: SET_PIN` | https://www.klipper3d.org/G-Codes.html |
| `KGC-PALETTE_CONNECT` | `PALETTE_CONNECT` | light | reference_only | `gcode_command_present: PALETTE_CONNECT` | https://www.klipper3d.org/G-Codes.html |
| `KGC-PALETTE_DISCONNECT` | `PALETTE_DISCONNECT` | light | reference_only | `gcode_command_present: PALETTE_DISCONNECT` | https://www.klipper3d.org/G-Codes.html |
| `KGC-PALETTE_CLEAR` | `PALETTE_CLEAR` | light | reference_only | `gcode_command_present: PALETTE_CLEAR` | https://www.klipper3d.org/G-Codes.html |
| `KGC-PALETTE_CUT` | `PALETTE_CUT` | light | reference_only | `gcode_command_present: PALETTE_CUT` | https://www.klipper3d.org/G-Codes.html |
| `KGC-PALETTE_SMART_LOAD` | `PALETTE_SMART_LOAD` | light | reference_only | `gcode_command_present: PALETTE_SMART_LOAD` | https://www.klipper3d.org/G-Codes.html |
| `KGC-PID_CALIBRATE` | `PID_CALIBRATE` | light | reference_only | `gcode_command_present: PID_CALIBRATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_PRINT_STATS_INFO` | `SET_PRINT_STATS_INFO` | light | reference_only | `gcode_command_present: SET_PRINT_STATS_INFO` | https://www.klipper3d.org/G-Codes.html |
| `KGC-Z_OFFSET_APPLY_PROBE` | `Z_OFFSET_APPLY_PROBE` | light | reference_only | `gcode_command_present: Z_OFFSET_APPLY_PROBE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-PROBE_EDDY_CURRENT_CALIBRATE` | `PROBE_EDDY_CURRENT_CALIBRATE` | light | reference_only | `gcode_command_present: PROBE_EDDY_CURRENT_CALIBRATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-PROBE_EDDY_CURRENT_TAP_CALIBRATE` | `PROBE_EDDY_CURRENT_TAP_CALIBRATE` | light | reference_only | `gcode_command_present: PROBE_EDDY_CURRENT_TAP_CALIBRATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-LDC_CALIBRATE_DRIVE_CURRENT` | `LDC_CALIBRATE_DRIVE_CURRENT` | light | reference_only | `gcode_command_present: LDC_CALIBRATE_DRIVE_CURRENT` | https://www.klipper3d.org/G-Codes.html |
| `KGC-QUERY_ADC` | `QUERY_ADC` | light | reference_only | `gcode_command_present: QUERY_ADC` | https://www.klipper3d.org/G-Codes.html |
| `KGC-QUERY_ENDSTOPS` | `QUERY_ENDSTOPS` | light | reference_only | `gcode_command_present: QUERY_ENDSTOPS` | https://www.klipper3d.org/G-Codes.html |
| `KGC-MEASURE_AXES_NOISE` | `MEASURE_AXES_NOISE` | light | reference_only | `gcode_command_present: MEASURE_AXES_NOISE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-TEST_RESONANCES` | `TEST_RESONANCES` | light | reference_only | `gcode_command_present: TEST_RESONANCES` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SHAPER_CALIBRATE` | `SHAPER_CALIBRATE` | light | reference_only | `gcode_command_present: SHAPER_CALIBRATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SAVE_VARIABLE` | `SAVE_VARIABLE` | light | reference_only | `gcode_command_present: SAVE_VARIABLE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SDCARD_LOOP_BEGIN` | `SDCARD_LOOP_BEGIN` | light | reference_only | `gcode_command_present: SDCARD_LOOP_BEGIN` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SDCARD_LOOP_END` | `SDCARD_LOOP_END` | light | reference_only | `gcode_command_present: SDCARD_LOOP_END` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SDCARD_LOOP_DESIST` | `SDCARD_LOOP_DESIST` | light | reference_only | `gcode_command_present: SDCARD_LOOP_DESIST` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_SERVO` | `SET_SERVO` | light | reference_only | `gcode_command_present: SET_SERVO` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_SKEW` | `SET_SKEW` | light | reference_only | `gcode_command_present: SET_SKEW` | https://www.klipper3d.org/G-Codes.html |
| `KGC-GET_CURRENT_SKEW` | `GET_CURRENT_SKEW` | light | reference_only | `gcode_command_present: GET_CURRENT_SKEW` | https://www.klipper3d.org/G-Codes.html |
| `KGC-CALC_MEASURED_SKEW` | `CALC_MEASURED_SKEW` | light | reference_only | `gcode_command_present: CALC_MEASURED_SKEW` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SKEW_PROFILE` | `SKEW_PROFILE` | light | reference_only | `gcode_command_present: SKEW_PROFILE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_SMART_EFFECTOR` | `SET_SMART_EFFECTOR` | light | reference_only | `gcode_command_present: SET_SMART_EFFECTOR` | https://www.klipper3d.org/G-Codes.html |
| `KGC-RESET_SMART_EFFECTOR` | `RESET_SMART_EFFECTOR` | light | reference_only | `gcode_command_present: RESET_SMART_EFFECTOR` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_STEPPER_ENABLE` | `SET_STEPPER_ENABLE` | light | reference_only | `gcode_command_present: SET_STEPPER_ENABLE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_TEMPERATURE_FAN_TARGET` | `SET_TEMPERATURE_FAN_TARGET` | light | reference_only | `gcode_command_present: SET_TEMPERATURE_FAN_TARGET` | https://www.klipper3d.org/G-Codes.html |
| `KGC-TEMPERATURE_PROBE_CALIBRATE` | `TEMPERATURE_PROBE_CALIBRATE` | light | reference_only | `gcode_command_present: TEMPERATURE_PROBE_CALIBRATE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-TEMPERATURE_PROBE_NEXT` | `TEMPERATURE_PROBE_NEXT` | light | reference_only | `gcode_command_present: TEMPERATURE_PROBE_NEXT` | https://www.klipper3d.org/G-Codes.html |
| `KGC-ABORT` | `ABORT` | light | reference_only | `gcode_command_present: ABORT` | https://www.klipper3d.org/G-Codes.html |
| `KGC-TEMPERATURE_PROBE_ENABLE` | `TEMPERATURE_PROBE_ENABLE` | light | reference_only | `gcode_command_present: TEMPERATURE_PROBE_ENABLE` | https://www.klipper3d.org/G-Codes.html |
| `KGC-DUMP_TMC` | `DUMP_TMC` | light | reference_only | `gcode_command_present: DUMP_TMC` | https://www.klipper3d.org/G-Codes.html |
| `KGC-INIT_TMC` | `INIT_TMC` | light | reference_only | `gcode_command_present: INIT_TMC` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_TMC_CURRENT` | `SET_TMC_CURRENT` | light | reference_only | `gcode_command_present: SET_TMC_CURRENT` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_TMC_FIELD` | `SET_TMC_FIELD` | light | reference_only | `gcode_command_present: SET_TMC_FIELD` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_VELOCITY_LIMIT` | `SET_VELOCITY_LIMIT` | light | reference_only | `gcode_command_present: SET_VELOCITY_LIMIT` | https://www.klipper3d.org/G-Codes.html |
| `KGC-TUNING_TOWER` | `TUNING_TOWER` | light | reference_only | `gcode_command_present: TUNING_TOWER` | https://www.klipper3d.org/G-Codes.html |
| `KGC-SET_Z_THERMAL_ADJUST` | `SET_Z_THERMAL_ADJUST` | light | reference_only | `gcode_command_present: SET_Z_THERMAL_ADJUST` | https://www.klipper3d.org/G-Codes.html |
