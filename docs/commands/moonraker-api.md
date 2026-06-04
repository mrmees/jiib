# Moonraker API Command Catalog

**Purpose:** Moonraker JSON-RPC, REST, and notification reference entries from the split external API docs.

## Sources

- https://moonraker.readthedocs.io/en/latest/external_api/authorization/
- https://moonraker.readthedocs.io/en/latest/external_api/database/
- https://moonraker.readthedocs.io/en/latest/external_api/devices/
- https://moonraker.readthedocs.io/en/latest/external_api/file_manager/
- https://moonraker.readthedocs.io/en/latest/external_api/history/
- https://moonraker.readthedocs.io/en/latest/external_api/integrations/
- https://moonraker.readthedocs.io/en/latest/external_api/job_queue/
- https://moonraker.readthedocs.io/en/latest/external_api/machine/
- https://moonraker.readthedocs.io/en/latest/external_api/printer/
- https://moonraker.readthedocs.io/en/latest/external_api/server/
- https://moonraker.readthedocs.io/en/latest/external_api/update_manager/
- https://moonraker.readthedocs.io/en/latest/external_api/webcams/

## Full-detail entries

| ID | Name | Tier | Runtime | Availability | Upstream |
|---|---|---|---|---|---|
| `MR-access.oneshot_token` | `access.oneshot_token` | full | registered | `always` | https://moonraker.readthedocs.io/en/latest/external_api/authorization/ |
| `MR-printer.emergency_stop` | `printer.emergency_stop` | full | registered | `always` | https://moonraker.readthedocs.io/en/latest/external_api/printer/ |
| `MR-printer.firmware_restart` | `printer.firmware_restart` | full | registered | `always` | https://moonraker.readthedocs.io/en/latest/external_api/printer/ |
| `MR-printer.gcode.help` | `printer.gcode.help` | full | planned_v1 | `always` | https://moonraker.readthedocs.io/en/latest/external_api/printer/ |
| `MR-printer.gcode.script` | `printer.gcode.script` | full | registered | `always` | https://moonraker.readthedocs.io/en/latest/external_api/printer/ |
| `MR-printer.info` | `printer.info` | full | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/printer/ |
| `MR-printer.objects.list` | `printer.objects.list` | full | registered | `always` | https://moonraker.readthedocs.io/en/latest/external_api/printer/ |
| `MR-printer.objects.query` | `printer.objects.query` | full | registered | `always` | https://moonraker.readthedocs.io/en/latest/external_api/printer/ |
| `MR-printer.objects.subscribe` | `printer.objects.subscribe` | full | registered | `always` | https://moonraker.readthedocs.io/en/latest/external_api/printer/ |
| `MR-printer.print.cancel` | `printer.print.cancel` | full | planned_v1 | `object_present: pause_resume` | https://moonraker.readthedocs.io/en/latest/external_api/printer/ |
| `MR-printer.print.pause` | `printer.print.pause` | full | planned_v1 | `object_present: pause_resume` | https://moonraker.readthedocs.io/en/latest/external_api/printer/ |
| `MR-printer.print.resume` | `printer.print.resume` | full | planned_v1 | `object_present: pause_resume` | https://moonraker.readthedocs.io/en/latest/external_api/printer/ |
| `MR-printer.print.start` | `printer.print.start` | full | planned_v1 | `object_present: virtual_sdcard` | https://moonraker.readthedocs.io/en/latest/external_api/printer/ |
| `MR-printer.restart` | `printer.restart` | full | registered | `always` | https://moonraker.readthedocs.io/en/latest/external_api/printer/ |
| `MR-server.connection.identify` | `server.connection.identify` | full | registered | `always` | https://moonraker.readthedocs.io/en/latest/external_api/server/ |
| `MR-server.files.delete_file` | `server.files.delete_file` | full | planned_v1 | `component_present: file_manager` | https://moonraker.readthedocs.io/en/latest/external_api/file_manager/ |
| `MR-server.files.get_directory` | `server.files.get_directory` | full | planned_v1 | `component_present: file_manager` | https://moonraker.readthedocs.io/en/latest/external_api/file_manager/ |
| `MR-server.files.list` | `server.files.list` | full | planned_v1 | `component_present: file_manager` | https://moonraker.readthedocs.io/en/latest/external_api/file_manager/ |
| `MR-server.files.metadata` | `server.files.metadata` | full | registered | `component_present: file_manager` | https://moonraker.readthedocs.io/en/latest/external_api/file_manager/ |
| `MR-server.files.roots` | `server.files.roots` | full | planned_v1 | `component_present: file_manager` | https://moonraker.readthedocs.io/en/latest/external_api/file_manager/ |
| `MR-server.files.thumbnails` | `server.files.thumbnails` | full | planned_v1 | `component_present: file_manager` | https://moonraker.readthedocs.io/en/latest/external_api/file_manager/ |
| `MR-server.gcode_store` | `server.gcode_store` | full | planned_v1 | `always` | https://moonraker.readthedocs.io/en/latest/external_api/server/ |
| `MR-server.history.list` | `server.history.list` | full | registered | `component_present: history` | https://moonraker.readthedocs.io/en/latest/external_api/history/ |
| `MR-server.info` | `server.info` | full | registered | `always` | https://moonraker.readthedocs.io/en/latest/external_api/server/ |
| `MR-server.spoolman.get_spool_id` | `server.spoolman.get_spool_id` | full | planned_v1 | `component_present: spoolman` | https://moonraker.readthedocs.io/en/latest/external_api/integrations/ |
| `MR-server.spoolman.post_spool_id` | `server.spoolman.post_spool_id` | full | planned_v1 | `component_present: spoolman` | https://moonraker.readthedocs.io/en/latest/external_api/integrations/ |
| `MR-server.spoolman.proxy` | `server.spoolman.proxy` | full | planned_v1 | `component_present: spoolman` | https://moonraker.readthedocs.io/en/latest/external_api/integrations/ |
| `MR-server.spoolman.status` | `server.spoolman.status` | full | planned_v1 | `component_present: spoolman` | https://moonraker.readthedocs.io/en/latest/external_api/integrations/ |
| `MR-server.temperature_store` | `server.temperature_store` | full | registered | `component_present: history` | https://moonraker.readthedocs.io/en/latest/external_api/server/ |
| `MR-server.webcams.get_item` | `server.webcams.get_item` | full | planned_v1 | `component_present: webcam` | https://moonraker.readthedocs.io/en/latest/external_api/webcams/ |
| `MR-server.webcams.list` | `server.webcams.list` | full | planned_v1 | `component_present: webcam` | https://moonraker.readthedocs.io/en/latest/external_api/webcams/ |
| `MR-server.webcams.test` | `server.webcams.test` | full | planned_v1 | `component_present: webcam` | https://moonraker.readthedocs.io/en/latest/external_api/webcams/ |

## Light/reference entries

| ID | Name | Tier | Runtime | Availability | Upstream |
|---|---|---|---|---|---|
| `MR-access.delete_user` | `access.delete_user` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/authorization/ |
| `MR-access.get_api_key` | `access.get_api_key` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/authorization/ |
| `MR-access.get_user` | `access.get_user` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/authorization/ |
| `MR-access.info` | `access.info` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/authorization/ |
| `MR-access.login` | `access.login` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/authorization/ |
| `MR-access.logout` | `access.logout` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/authorization/ |
| `MR-access.post_api_key` | `access.post_api_key` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/authorization/ |
| `MR-access.post_user` | `access.post_user` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/authorization/ |
| `MR-access.refresh_jwt` | `access.refresh_jwt` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/authorization/ |
| `MR-access.user.password` | `access.user.password` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/authorization/ |
| `MR-access.users.list` | `access.users.list` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/authorization/ |
| `MR-debug.database.delete_item` | `debug.database.delete_item` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/database/ |
| `MR-debug.database.get_item` | `debug.database.get_item` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/database/ |
| `MR-debug.database.list` | `debug.database.list` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/database/ |
| `MR-debug.database.post_item` | `debug.database.post_item` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/database/ |
| `MR-debug.database.table` | `debug.database.table` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/database/ |
| `MR-debug.notifiers.test` | `debug.notifiers.test` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/integrations/ |
| `MR-machine.device_power.devices` | `machine.device_power.devices` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-machine.device_power.get_device` | `machine.device_power.get_device` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-machine.device_power.off` | `machine.device_power.off` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-machine.device_power.on` | `machine.device_power.on` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-machine.device_power.post_device` | `machine.device_power.post_device` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-machine.device_power.status` | `machine.device_power.status` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-machine.peripherals.canbus` | `machine.peripherals.canbus` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/machine/ |
| `MR-machine.peripherals.serial` | `machine.peripherals.serial` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/machine/ |
| `MR-machine.peripherals.usb` | `machine.peripherals.usb` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/machine/ |
| `MR-machine.peripherals.video` | `machine.peripherals.video` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/machine/ |
| `MR-machine.proc_stats` | `machine.proc_stats` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/machine/ |
| `MR-machine.reboot` | `machine.reboot` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/machine/ |
| `MR-machine.services.restart` | `machine.services.restart` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/machine/ |
| `MR-machine.services.start` | `machine.services.start` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/machine/ |
| `MR-machine.services.stop` | `machine.services.stop` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/machine/ |
| `MR-machine.shutdown` | `machine.shutdown` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/machine/ |
| `MR-machine.sudo.info` | `machine.sudo.info` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/machine/ |
| `MR-machine.sudo.password` | `machine.sudo.password` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/machine/ |
| `MR-machine.system_info` | `machine.system_info` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/machine/ |
| `MR-machine.td1.data` | `machine.td1.data` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/integrations/ |
| `MR-machine.td1.reboot` | `machine.td1.reboot` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/integrations/ |
| `MR-machine.update.client` | `machine.update.client` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/update_manager/ |
| `MR-machine.update.full` | `machine.update.full` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/update_manager/ |
| `MR-machine.update.klipper` | `machine.update.klipper` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/update_manager/ |
| `MR-machine.update.moonraker` | `machine.update.moonraker` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/update_manager/ |
| `MR-machine.update.recover` | `machine.update.recover` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/update_manager/ |
| `MR-machine.update.refresh` | `machine.update.refresh` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/update_manager/ |
| `MR-machine.update.rollback` | `machine.update.rollback` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/update_manager/ |
| `MR-machine.update.status` | `machine.update.status` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/update_manager/ |
| `MR-machine.update.system` | `machine.update.system` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/update_manager/ |
| `MR-machine.update.upgrade` | `machine.update.upgrade` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/update_manager/ |
| `MR-machine.wled.get_strip` | `machine.wled.get_strip` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-machine.wled.off` | `machine.wled.off` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-machine.wled.on` | `machine.wled.on` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-machine.wled.post_strip` | `machine.wled.post_strip` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-machine.wled.status` | `machine.wled.status` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-machine.wled.strips` | `machine.wled.strips` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-machine.wled.toggle` | `machine.wled.toggle` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-printer.query_endstops.status` | `printer.query_endstops.status` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/printer/ |
| `MR-server.analysis.dump_config` | `server.analysis.dump_config` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/integrations/ |
| `MR-server.analysis.estimate` | `server.analysis.estimate` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/integrations/ |
| `MR-server.analysis.process` | `server.analysis.process` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/integrations/ |
| `MR-server.analysis.status` | `server.analysis.status` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/integrations/ |
| `MR-server.config` | `server.config` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/server/ |
| `MR-server.database.compact` | `server.database.compact` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/database/ |
| `MR-server.database.delete_backup` | `server.database.delete_backup` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/database/ |
| `MR-server.database.delete_item` | `server.database.delete_item` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/database/ |
| `MR-server.database.get_item` | `server.database.get_item` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/database/ |
| `MR-server.database.list` | `server.database.list` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/database/ |
| `MR-server.database.post_backup` | `server.database.post_backup` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/database/ |
| `MR-server.database.post_item` | `server.database.post_item` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/database/ |
| `MR-server.database.restore` | `server.database.restore` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/database/ |
| `MR-server.files.copy` | `server.files.copy` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/file_manager/ |
| `MR-server.files.delete_directory` | `server.files.delete_directory` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/file_manager/ |
| `MR-server.files.metascan` | `server.files.metascan` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/file_manager/ |
| `MR-server.files.move` | `server.files.move` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/file_manager/ |
| `MR-server.files.post_directory` | `server.files.post_directory` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/file_manager/ |
| `MR-server.files.zip` | `server.files.zip` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/file_manager/ |
| `MR-server.history.delete_job` | `server.history.delete_job` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/history/ |
| `MR-server.history.get_job` | `server.history.get_job` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/history/ |
| `MR-server.history.reset_totals` | `server.history.reset_totals` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/history/ |
| `MR-server.history.totals` | `server.history.totals` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/history/ |
| `MR-server.job_queue.delete_job` | `server.job_queue.delete_job` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/job_queue/ |
| `MR-server.job_queue.jump` | `server.job_queue.jump` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/job_queue/ |
| `MR-server.job_queue.pause` | `server.job_queue.pause` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/job_queue/ |
| `MR-server.job_queue.post_job` | `server.job_queue.post_job` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/job_queue/ |
| `MR-server.job_queue.start` | `server.job_queue.start` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/job_queue/ |
| `MR-server.job_queue.status` | `server.job_queue.status` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/job_queue/ |
| `MR-server.logs.rollover` | `server.logs.rollover` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/server/ |
| `MR-server.mqtt.publish` | `server.mqtt.publish` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-server.mqtt.subscribe` | `server.mqtt.subscribe` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-server.notifiers.list` | `server.notifiers.list` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/integrations/ |
| `MR-server.restart` | `server.restart` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/server/ |
| `MR-server.sensors.info` | `server.sensors.info` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-server.sensors.list` | `server.sensors.list` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-server.sensors.measurements` | `server.sensors.measurements` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/devices/ |
| `MR-server.webcams.delete_item` | `server.webcams.delete_item` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/webcams/ |
| `MR-server.webcams.post_item` | `server.webcams.post_item` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/webcams/ |
| `MR-server.websocket.id` | `server.websocket.id` | light | reference_only | `always` | https://moonraker.readthedocs.io/en/latest/external_api/server/ |

## Server-push notifications

Moonraker pushes these over the websocket as JSON-RPC notifications (no `id`). Per the live
contract, `params` is **always a 1-element array** carrying a single object, even when only one
object is delivered (verified against `docs/commands/spoolman-live-ender5-notify.json`).

| ID | Method | Tier | Runtime | Availability | Upstream |
|---|---|---|---|---|---|
| `MR-notify_active_spool_set` | `notify_active_spool_set` | full | planned_v1 | `component_present: spoolman` | https://moonraker.readthedocs.io/en/latest/external_api/integrations/ |
| `MR-notify_spoolman_status_changed` | `notify_spoolman_status_changed` | full | planned_v1 | `component_present: spoolman` | https://moonraker.readthedocs.io/en/latest/external_api/integrations/ |

**`notify_active_spool_set`** — fired when the active spool changes (set, cleared, or reported by
another client). `params` is a 1-element array carrying `{spool_id: Int}`. A cleared active spool
is reported as `spool_id: null`.

```json
{"jsonrpc":"2.0","method":"notify_active_spool_set","params":[{"spool_id":1}]}
```

**`notify_spoolman_status_changed`** — fired when Moonraker's connection to the Spoolman server
changes. `params` is a 1-element array carrying `{spoolman_connected: Boolean}` (and may carry
`pending_reports`). Mirrors the `server.spoolman.status` reply shape.

```json
{"jsonrpc":"2.0","method":"notify_spoolman_status_changed","params":[{"spoolman_connected":false}]}
```

Unrelated push notifications (e.g. `notify_proc_stat_update`) carry the same 1-element-array
`params` shape but must be ignored by the Spoolman notify router (golden:
`spoolman-live-ender5-notify.json` interleaves nine `notify_proc_stat_update` frames around the
two `notify_active_spool_set` frames).
