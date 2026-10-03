# Independent geographic / landmark opportunities

Area Chapters remain the main content container. A grounded spatial node can wake the same Director
inside an unchanged chapter, without satisfying the usual 1.5–3km real-GPS or 9km simulated distance gate.
The existing minimum 45-second cadence, voice cooldown, quiet, listening, image interaction, pending-request,
location freshness and user-intent gates still apply. This creates an opportunity; it never forces narration.

`LandmarkKind` supports mountains/peaks, rivers/crossings, lakes/reservoirs, landforms, bridges/dams/tunnels,
landmark buildings, museums, ruins and heritage sites. `landmarks.json` is a small, source-backed reference set
independent of the route fixture. It is consumed by both real GPS and developer simulation. No new Provider,
map SDK, backend, route planner or full POI database is involved.

The shipped V0.2 region contains four **coarse** references: 三星堆、绵竹年画村、龙门山山前地带、雎水太平桥.
Their source and uncertainty are carried into Context. Coordinate/radius values are regional test references,
not surveyed site entrances, bridge crossing geometry or sightlines. No nationwide POI discovery is claimed.
Outside grounded reference coverage, ordinary Area Chapters continue; the app does not invent coordinates.
Additional validated references can use any supported kind through the same bounded catalog format.

Each newly live node has one independent opportunity per journey. Dispatch consumes the opportunity even
when the response is SILENT/failure, preventing a retry storm. Live nodes remain candidates for ordinary checks;
they are not a pending narration queue. A node becomes ineligible outside its radius or when distance grows
over 250m from the closest live fix (noise tolerance). A response tied to a no-longer-live requested node is
STALE even if total travel is less than the original 1500m stale threshold. Spoken node IDs suppress automatic
retitled repeats; active user deepening remains available. End/reset clears all spatial and entity state.

The current schema adds `landmark_id`; local parsing retains strict six/seven-field legacy compatibility.
The Director must choose a supplied ID for a proximity topic, otherwise use an empty string. Unrequested IDs
cannot create an automatic spatial event. Local deterministic geometry decides eligibility; the Director decides
whether and how to explain a worthwhile thing. Grounded high-value candidates outrank another generic road or
settlement mechanism; topic diversity remains a preference rather than a quota.

Focused tests cover all 14 kinds inside the same chapter, the 45s gate, SILENT without retries, quiet passing
without replay, image/listening priority, delayed receding response rejection, entity dedup/active deepening,
end/reset, legacy schema and invalid catalog data. Full production-catalog simulation runs at 100km/h / 16×
with a deterministic test Provider, 5s latency and fake 20s Voice; its report is `artifacts/landmark-simulation.txt`.
Actual provider editorial quality and phone GPS proximity timing are **DEFERRED TO COMBINED PHONE ACCEPTANCE**.
