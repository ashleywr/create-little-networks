# Create: Little Networks

Create: Little Networks connects Create's addressed packages to Little Logistics docking stations
on NeoForge 1.21.1.

## Current behavior

### Create-compatible package couriers

1. Create a Little Logistics **Network Chart** by using a blank chart on rail or navigable water.
   Click stations and junction waypoints to add nodes, then select a source and destination to
   compile a directed edge. Sneak while selecting the destination to compile both directions.
   Put a Conductor's Wrench in the offhand to edit destructively: select two nodes to remove their
   edge, or sneak-use an unselected node to remove it. Rename the chart in an anvil and use it once
   to rename the shared network.
2. Put the chart in a locomotive or tug route slot. Copies of the chart refer to the same persistent
   graph, so every enrolled vehicle uses the same edits.
3. Configure the same cyclic Create Schedule used by a Create mail train: **Retrieve Packages →
   Deliver Packages**. The retrieval instruction's address filter is preserved.
4. Use the Schedule on a locomotive or tug while it is docked at any station on that chart.

The courier waits at its current station when no work exists. It loads ordinary Create packages,
chooses reachable destinations from their addresses, visits additional destinations when packages
for multiple addresses are aboard, and resumes looking for work from its new location. Several
couriers can share a chart; counted reservations prevent them from claiming the same observed
shipment. Rail and water graphs remain independent.

The supported schedule conditions are Scheduled Delay and Cargo Inactivity. Use an empty hand on
an idle, docked courier to retrieve its Schedule.

### Optional depot dispatch

Prefix the schedule with **Little Logistics Depot** to retain the LTN-style pooled behavior. The
depot filter must match exactly one station, the empty vehicle must be enrolled there, and it returns
there after each completed shipment. This mode is optional and is not required for Create parity.

### Network Inspector

Right-click a populated chart in the air to open the read-only Network Inspector. Its Summary,
Nodes, Edges, and Logistics tabs show the graph, endpoints, package demand, reservations, courier
mode and state, and active jobs. Close and reopen it to refresh the snapshot. `/cln inspect` opens
the same view for the chart in either hand; without one it opens an empty diagnostic view.

### Mimic Panels

Mimic Panels form thin, surface-mounted industrial dashboards. Adjacent coplanar panels merge into
rectangular regions up to 8 by 8 blocks, while each block contributes an 8 by 8 editing grid. The
region keeps one shared widget document and automatically repartitions if a panel is removed.

- Craft and place **Mimic Panels** on any supported face, like item frames.
- Hold a **Panel Designer** and press **V**, or sneak-right-click a panel, to choose a widget or an
  edit tool from the radial menu.
- Point toward a radial wedge and click to select it. Click the center, right-click, or press Esc
  to return from a submenu; right-click or Esc closes the root menu. Arrow keys or the mouse wheel
  move the selection, and Enter confirms it.
- Right-click a cell to place the selected widget. The first-pass tools can delete, rotate,
  recolor, or duplicate an existing widget.
- With a redstone-widget bind tool selected, right-click while holding a Create Redstone Link
  frequency item in the other hand to assign its first or second frequency.
- Use a Little Networks **Network Chart** directly on a panel to copy its network reference. Network
  widgets can then cycle through compatible nodes, edges, couriers, and summary metrics.
- Buttons, switches, selectors, and dials expose virtual Redstone Link transmitters. Hold sneak
  while operating a selector or dial to move it in reverse.

The renderer clips a region-wide widget document to each tile, so traces and widgets cross block
seams without creating one large block entity or display entity. Network bindings are read-only;
redstone controls are the deliberate automation output path.

### Addressed package ports (recommended)

- Hold a Create Postbox and use it on any part of a Little Logistics docking station, then place
  the Postbox within Create's normal package-port range.
- Configure the Postbox address normally. It is persisted as a dedicated Little Logistics package
  port target and advertised to the dock at runtime.
- When a cargo barge or cargo wagon docks, packages not addressed to that Postbox are loaded for
  departure. Packages matching its address are unloaded into it. These are the same import/export
  rules used by Create train stations.
- Multiple Postboxes can target one dock, including wildcard addresses.

### Packagers directly on docks

- Place a Create Packager so its inventory-facing side points directly into a Little Logistics
  docking-station controller.
- The Little Logistics station name is the package address. Rename the station in its normal GUI.
- When a cargo barge or cargo wagon is docked, completed packages are loaded into its inventory.
- At another dock, packages whose Create address matches that station name are handed to the
  attached Packager and unpacked into the docked cargo inventory.
- Create's normal address globbing is preserved (for example, `Harbor *`).
- A successful package movement resets Little Logistics' dock idle timer, so the vehicle waits for
  active loading/unloading and departs normally afterward.

Ordinary vehicles using classic cyclic Little Logistics routes retain the opportunistic behavior
described above. Demand-driven dispatch is enabled only for vehicles with a Network Chart and an
installed courier Schedule. Postboxes are preferred when packages should remain packaged; a
directly attached Packager unpacks matching arrivals into the docked cargo inventory.

## Building

The workspace intentionally keeps reference repositories outside this repository. By default the
build compiles against:

`../../LittleLogistics/repo/build/libs/littlelogistics-mc1.21.1-v1.21.1.1-beta.5-slim.jar`

Build Little Logistics first if that file is absent, then run:

```powershell
.\gradlew.bat build
```

Override `little_logistics_jar` in `gradle.properties` or on the Gradle command line to use another
compatible development jar.

## Client renderer checks

`./gradlew runClient` includes the pinned NeoForge Sodium 0.6.13 and Iris 1.8.12 test stack by
default. These dependencies belong only to the client run: they are not bundled into the mod,
published as dependencies, or added to the dedicated-server run. Gradle stages the two jars as
`cln-test-sodium.jar` and `cln-test-iris.jar` in `run/client/mods` before launching. Use
`./gradlew runClient -Prenderer_compat=false` for the vanilla-renderer comparison, which uses
the separate `run/client-vanilla` game directory and its own worlds and configuration.

Check every new renderer with the vanilla renderer, Sodium + Iris with shaders disabled, and
Sodium + Iris with an actual shader pack enabled. For Mimic Panels, check all six mounting faces,
depth and occlusion against the backing block, text orientation, transparent zones and placement
previews, widgets crossing tile seams, and reload/chunk-unload behavior. Record the shader pack
and version used; a successful launch with Iris alone does not establish shader compatibility.

## Reference sources used

- `../../LittleLogistics/repo`
- `../../Amphibia/internal/reference-sources/create-1.21.1-6.0.10-280`

These sources are references only and are not copied into or published with this repository.

The Panel Designer radial renderer is adapted from `../../KeyBindBundlesFabric`'s MIT-licensed
`RadialMenuRenderer`. Its copyright and license are included in the mod at
`META-INF/licenses/KeyBindBundles.txt`.

<!-- ashley-release-information -->

## My builds

This is my original project. Original project code is offered under the [MIT License](LICENSE). Third-party material retains its own license and attribution.

Provided **AS IS**, without warranty of any kind. Maintenance, updates and support are best effort; compatibility with every modpack is not guaranteed.

### Downloads and installation

Download a runtime `.jar` from [GitHub Releases](https://github.com/ashleywr/create-little-networks/releases). Use the Minecraft version and loader named in that release and install its required dependencies. Do not install `-sources`, `-dev`, `-slim`, `-common` or test JARs as the mod.

Close Minecraft, back up your world, and place the matching runtime JAR in the instance's `mods/` directory. Replace older versions of this same mod rather than loading duplicates. For content mods, install matching versions on the server and its clients unless the release explicitly says client-only or server-only.

### Building and reporting problems

Use Java 21 and the checked-in Gradle wrapper:

```powershell
.\gradlew.bat build
```

Build outputs are under `build/libs/` or each loader subproject's `build/libs/`. Some projects require the local development dependencies described above; those dependencies are not included in the download.

For a problem report, include the exact release filename, Minecraft and loader versions, relevant mod versions, `logs/latest.log`, and a crash report if one was written. Remove private information before sharing logs. Remove the JAR to uninstall; worlds containing this mod's blocks, entities or world generation may require restoring a backup.
