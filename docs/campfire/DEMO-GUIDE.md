# Demonstrate Mastek Campfire

Start `cd web && npm install && npm run dev`. Open the Vite Local URL. The app starts with the Campfire engagement demo. The top link opens the existing employee/publishing workflows. The same engagement demo can run independently at `/campfire/index.html` on any static HTTP server.

This is a browser-local synthetic POC, independent of the old API client's fallback state. It is not a Java-backed implementation. Use fictional content only. No external messages are sent. Reset clears only `campfire_demo_v1` on this browser origin.

1. Reset. Asha submits the prepared badminton story. Show zero points while pending.
2. Switch to Priya → Review & publish → Approve revision 1. Approval earns no points.
3. Switch to Marcus → Publish approved revision. Asha now has 20 points and First Spark.
4. Switch to Daniel → Around the campfire → appreciate Asha's story. Asha becomes 25; Daniel becomes 25 including his seeded contribution. Repeat click is disabled.
5. Switch to Asha → Community champions. Show optional ranking and hide/unhide.
6. Meet Saathi → Summarize bulletin. Only published stories appear. Optional read-aloud uses browser speech, not Piper. Type Open leaderboard or Open challenge.
7. Optional: as Asha appreciate Daniel, Elena and Marcus to unlock Connection Maker. As each of the five other personas appreciate Asha to unlock Community Favourite.
8. Optional: Marcus withdraws Asha's story. It disappears from stories and Saathi; related points reverse. Reset for the next run.

Recognition week: Monday 00:00 UTC. Refresh retains current-week state. A later week initializes fresh synthetic seeds. No historical leaderboard is retained in this POC.

## Say accurately
- Working: synthetic browser submission/review/publication, scoring, caps, badges, optional leaderboard, reset, deterministic typed Saathi, browser read-aloud where supported.
- Pending: Java persistence/authorization, integrated voice service, live Azure assessment, microphone/Piper validation and production controls.
- The earlier handover's 21 mocked adapter tests were not available in this checkout and were not rerun.

See IMPLEMENTATION-STORIES.md for delivery scope and NEW-CONTRACTS.md for proposed API/storage changes.
