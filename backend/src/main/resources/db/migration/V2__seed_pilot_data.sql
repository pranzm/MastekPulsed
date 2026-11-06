-- ============================================================================
-- INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
-- ============================================================================
-- File: backend/src/main/resources/db/migration/V2__seed_pilot_data.sql
-- Purpose: Seeds the exact synthetic pilot dataset required by the Mastek Pulse
--          Proposal (Pages 2, 3, 6-11) and Hackathon Scope Table:
--   - 5 Canonical Personas (`usr_asha`, `usr_daniel`, `usr_priya`, `usr_marcus`, `usr_elena`)
--   - 3 Audience Groups (`aud_all`, `aud_pilot`, `aud_uk_leadership`)
--   - 1 Mastek Radio Queue (3 curated commute items, 12 mins total)
--   - 3 Culture Cast Episodes (with chapters, transcripts, and related stories)
--   - 6 Bite-Sized Company Updates (official source, date, summary, links)
--   - 12 Synthetic Employee Contributions (Stories/Moments), including Daniel's
--     flagship retrospective story ("Ask me about this — 2 places available")
--     plus items in `IN_REVIEW`, `APPROVED`, and `DRAFT` for the Publishing Portal.
-- ============================================================================

-- 1. SYNTHETIC EMPLOYEE PROFILES
INSERT INTO users (id, display_name, email, region, function_name, roles_csv, interests_csv, notification_frequency, preferred_language, host_paused, weekly_host_minutes) VALUES
('usr_asha', 'Asha Patel', 'asha.patel@mastek-pulse.demo', 'INDIA', 'Engineering', 'EMPLOYEE,CREATOR', 'team learning,innovation,culture', 'DAILY_DIGEST', 'en', FALSE, 15),
('usr_daniel', 'Daniel Wright', 'daniel.wright@mastek-pulse.demo', 'UK', 'Delivery', 'EMPLOYEE,CREATOR', 'team learning,delivery excellence,agile', 'DAILY_DIGEST', 'en', FALSE, 35),
('usr_priya', 'Priya Nair', 'priya.nair@mastek-pulse.demo', 'INDIA', 'People & Culture', 'EMPLOYEE,CREATOR,REVIEWER', 'culture,inclusion,onboarding', 'IMMEDIATE', 'en', FALSE, 40),
('usr_marcus', 'Marcus Vance', 'marcus.vance@mastek-pulse.demo', 'US', 'Operations', 'EMPLOYEE,PUBLISHER,MODERATOR', 'innovation,cloud architecture,operations', 'DAILY_DIGEST', 'en', FALSE, 30),
('usr_elena', 'Elena Rostova', 'elena.rostova@mastek-pulse.demo', 'UK', 'Platform IT', 'EMPLOYEE,ADMIN', 'security,accessibility,cloud architecture', 'WEEKLY_SUMMARY', 'en', FALSE, 20),
('usr_rohan', 'Rohan Deshmukh', 'rohan.deshmukh@mastek-pulse.demo', 'INDIA', 'Cloud Practice', 'EMPLOYEE,CREATOR', 'innovation,cloud architecture,ai engineering', 'DAILY_DIGEST', 'en', FALSE, 25),
('usr_sophie', 'Sophie Clarke', 'sophie.clarke@mastek-pulse.demo', 'UK', 'Healthcare Delivery', 'EMPLOYEE,CREATOR', 'customer stories,delivery excellence,accessibility', 'DAILY_DIGEST', 'en', FALSE, 30),
('usr_carlos', 'Carlos Mendez', 'carlos.mendez@mastek-pulse.demo', 'US', 'Data & AI', 'EMPLOYEE,CREATOR', 'innovation,ai engineering,team learning', 'DAILY_DIGEST', 'en', FALSE, 45);

-- 2. AUDIENCE GROUPS & MEMBERSHIP
INSERT INTO audiences (id, name, description, region_scope) VALUES
('aud_all', 'All Employees', 'Approved company-wide updates, Mastek Radio, and Culture Cast episodes.', 'ALL'),
('aud_pilot', 'Colleagues in the Pilot', '60 pilot volunteers across India, UK, and US testing cross-team connections.', 'ALL'),
('aud_uk_restricted', 'UK Delivery Governance', 'Scoped UK delivery governance group (used to test denied access for non-members).', 'UK');

INSERT INTO audience_members (audience_id, user_id) VALUES
('aud_all', 'usr_asha'), ('aud_all', 'usr_daniel'), ('aud_all', 'usr_priya'), ('aud_all', 'usr_marcus'), ('aud_all', 'usr_elena'), ('aud_all', 'usr_rohan'), ('aud_all', 'usr_sophie'), ('aud_all', 'usr_carlos'),
('aud_pilot', 'usr_asha'), ('aud_pilot', 'usr_daniel'), ('aud_pilot', 'usr_priya'), ('aud_pilot', 'usr_marcus'), ('aud_pilot', 'usr_elena'), ('aud_pilot', 'usr_rohan'), ('aud_pilot', 'usr_sophie'), ('aud_pilot', 'usr_carlos'),
('aud_uk_restricted', 'usr_daniel'), ('aud_uk_restricted', 'usr_elena');

-- ============================================================================
-- 3. ONE MASTEK RADIO QUEUE (Wireframe 07 - Page 9: "Your Commute Edition - 12 min")
-- ============================================================================
INSERT INTO content_items (id, author_id, audience_id, content_type, status, current_version, published_version, published_at) VALUES
('rad_commute_01', 'usr_marcus', 'aud_all', 'RADIO_QUEUE', 'PUBLISHED', 1, 1, DATEADD('HOUR', -6, CURRENT_TIMESTAMP));

INSERT INTO content_revisions (id, content_id, version_number, title, summary, body_text, tags_csv, official_source, has_audio, media_asset_key, duration_seconds, transcript_text, chapters_json, approved_by, approved_at) VALUES
('rev_rad_01', 'rad_commute_01', 1,
 'Your Commute Edition: A little closer to Mastek',
 'Company news + colleague stories · Curated queue / 12 min remaining',
 'Curated 12-minute commute edition combining three approved highlights: (01) Company pulse (2 min), (02) Innovation spotlight (4 min), and (03) A story across borders (6 min).',
 'commute,company news,innovation,culture',
 'Mastek Internal Communications',
 TRUE, 'audio/mastek-radio-commute-01.mp3', 720,
 '[00:00] Welcome to Mastek Radio — Your Commute Edition. Today we bring you three approved highlights in twelve minutes.\n[00:15] 01 / Company Pulse (2 min): Three ideas worth knowing today across our global delivery centers.\n[02:15] 02 / Innovation Spotlight (4 min): How teams are using structured templates and human-reviewed AI to cut documentation handoff time.\n[06:15] 03 / A Story Across Borders (6 min): Daniel in UK Delivery and Asha in India Engineering share how asynchronous writing transformed their team retrospectives.',
 '[{"index":1,"title":"01 Company pulse","durationMinutes":2,"startSeconds":0},{"index":2,"title":"02 Innovation spotlight","durationMinutes":4,"startSeconds":120},{"index":3,"title":"03 A story across borders","durationMinutes":6,"startSeconds":360}]',
 'usr_priya', DATEADD('HOUR', -7, CURRENT_TIMESTAMP));

-- ============================================================================
-- 4. THREE CULTURE CAST EPISODES (Wireframe 08 - Page 9 & 10 & 11)
-- ============================================================================
INSERT INTO content_items (id, author_id, audience_id, content_type, status, current_version, published_version, published_at) VALUES
('cc_ep_01', 'usr_priya', 'aud_all', 'CULTURE_CAST', 'PUBLISHED', 1, 1, DATEADD('DAY', -10, CURRENT_TIMESTAMP)),
('cc_ep_02', 'usr_sophie', 'aud_all', 'CULTURE_CAST', 'PUBLISHED', 1, 1, DATEADD('DAY', -5, CURRENT_TIMESTAMP)),
('cc_ep_03', 'usr_daniel', 'aud_all', 'CULTURE_CAST', 'PUBLISHED', 3, 2, DATEADD('DAY', -1, CURRENT_TIMESTAMP));

INSERT INTO content_revisions (id, content_id, version_number, title, summary, body_text, tags_csv, official_source, has_audio, media_asset_key, duration_seconds, transcript_text, chapters_json, related_story_id, approved_by, approved_at) VALUES
('rev_cc_01', 'cc_ep_01', 1,
 'Episode 01: First 90 Days Across Continents',
 'How new Mastek colleagues find mentors beyond their immediate project team.',
 'Two engineers in Pune and Reading reflect on the small questions they hesitated to ask in their first month—and how structured 15-minute introductions changed their onboarding.',
 'culture,onboarding,team learning', 'Culture Cast Studio', TRUE, 'audio/culture-cast-ep01.mp3', 540,
 '[00:00] Priya: Welcome to Culture Cast Episode 1. Today we are talking about the first 90 days at Mastek.\n[02:30] Guest: The biggest unlock for me was realizing people in other regions are happy to share a 15-minute walkthrough if the topic is specific.',
 '[{"index":1,"title":"Introduction","startSeconds":0},{"index":2,"title":"The hesitation to reach out","startSeconds":150},{"index":3,"title":"Making asks specific","startSeconds":340}]',
 'story_01', 'usr_marcus', DATEADD('DAY', -10, CURRENT_TIMESTAMP)),

('rev_cc_02', 'cc_ep_02', 1,
 'Episode 02: Designing Accessible Healthcare Portals',
 'Lessons from UK and India delivery pods on building WCAG-first patient experiences.',
 'Sophie Clarke hosts a conversation on why accessibility checks belong at wireframe stage rather than pre-release QA.',
 'accessibility,delivery excellence,innovation', 'Culture Cast Studio', TRUE, 'audio/culture-cast-ep02.mp3', 600,
 '[00:00] Sophie: In Episode 2 of Culture Cast, we explore how our healthcare engineering pods bake screen-reader and keyboard navigation into every component.\n[03:10] Engineer: Testing with TalkBack and VoiceOver early saved us three weeks of rework.',
 '[{"index":1,"title":"Why WCAG matters early","startSeconds":0},{"index":2,"title":"Keyboard & screen-reader patterns","startSeconds":190},{"index":3,"title":"Sharing checklists across teams","startSeconds":420}]',
 'story_03', 'usr_priya', DATEADD('DAY', -5, CURRENT_TIMESTAMP)),

-- Episode 03/04: Revision 2 is PUBLISHED (12 min / 8 min), while Revision 3 is IN_REVIEW (Matches Page 9, 10, 11 wireframes!)
('rev_cc_03_v2', 'cc_ep_03', 2,
 'Episode 03: Different places. Shared possibilities.',
 'A conversation about ideas and culture across geographies and functions · 8 min / Hosted by a colleague',
 'Daniel Wright and Priya Nair discuss how quiet writing before open discussion helps distributed teams across India, the UK, and the US contribute on equal footing.',
 'culture,team learning,inclusion', 'Culture Cast Studio', TRUE, 'audio/culture-cast-ep03.mp3', 480,
 '[00:00] Daniel: Welcome to Culture Cast. Today we are looking at how different offices collaborate across time zones.\n[02:10] Priya: When we gave people five minutes to write notes silently before speaking, participation from quieter colleagues doubled.\n[05:30] Daniel: You can explore the related retrospective story right below this episode and book a 15-minute chat.',
 '[{"index":1,"title":"Bridging time zones","startSeconds":0},{"index":2,"title":"Write first, then share (02:10)","startSeconds":130},{"index":3,"title":"How to try it this week","startSeconds":330}]',
 'story_01', 'usr_priya', DATEADD('DAY', -1, CURRENT_TIMESTAMP)),

('rev_cc_03_v3', 'cc_ep_03', 3,
 'Culture Cast / Episode 04: Different places. Shared possibilities. (Extended Transcript)',
 'Audio + transcript · Updated chapter markers and follow-up guide for pilot teams.',
 'Revision 3 adds the downloadable retrospective checklist link and updated chapter timestamps for the Publishing Portal review queue demonstration (Page 11).',
 'culture,team learning,inclusion', 'Culture Cast Studio', TRUE, 'audio/culture-cast-ep03.mp3', 480,
 '[00:00] Daniel: Welcome to Culture Cast. Today we are looking at how different offices collaborate across time zones.\n[02:10] Priya: When we gave people five minutes to write notes silently before speaking, participation from quieter colleagues doubled.\n[05:30] Daniel: Explore the linked story to try this format with your team.',
 '[{"index":1,"title":"Bridging time zones","startSeconds":0},{"index":2,"title":"Write first, then share","startSeconds":130},{"index":3,"title":"Pilot checklist","startSeconds":330}]',
 'story_01', NULL, NULL);

-- ============================================================================
-- 5. SIX BITE-SIZED COMPANY UPDATES (Page 3 & Page 10 Wireframe)
-- ============================================================================
INSERT INTO content_items (id, author_id, audience_id, content_type, status, current_version, published_version, published_at) VALUES
('upd_01', 'usr_marcus', 'aud_all', 'UPDATE', 'PUBLISHED', 1, 1, DATEADD('HOUR', -3, CURRENT_TIMESTAMP)),
('upd_02', 'usr_priya', 'aud_all', 'UPDATE', 'PUBLISHED', 1, 1, DATEADD('DAY', -1, CURRENT_TIMESTAMP)),
('upd_03', 'usr_elena', 'aud_all', 'UPDATE', 'PUBLISHED', 1, 1, DATEADD('DAY', -2, CURRENT_TIMESTAMP)),
('upd_04', 'usr_rohan', 'aud_all', 'UPDATE', 'PUBLISHED', 1, 1, DATEADD('DAY', -3, CURRENT_TIMESTAMP)),
('upd_05', 'usr_sophie', 'aud_all', 'UPDATE', 'PUBLISHED', 1, 1, DATEADD('DAY', -4, CURRENT_TIMESTAMP)),
('upd_06', 'usr_carlos', 'aud_all', 'UPDATE', 'IN_REVIEW', 1, NULL, NULL);

INSERT INTO content_revisions (id, content_id, version_number, title, summary, body_text, tags_csv, official_source, external_url, has_audio, duration_seconds, transcript_text, approved_by, approved_at) VALUES
('rev_upd_01', 'upd_01', 1,
 'Three ideas worth knowing today',
 'Company Update / 2 min · Quarterly engineering guilds, responsible AI guardrails, and cross-region learning hours.',
 '1. Global Engineering Guilds open registration this Friday.\n2. Our Responsible AI checklist now includes mandatory human review and source links for internal summaries.\n3. Pilot teams can allocate two 15-minute cross-team learning slots per week.',
 'innovation,culture,team learning', 'Official Source · Internal Comms', 'https://intranet.mastek.demo/updates/three-ideas', TRUE, 120,
 'Three ideas worth knowing today: Global Engineering Guild registration opens Friday; Responsible AI guardrails emphasize human choice; and pilot volunteers can host two 15-minute conversations per week.',
 'usr_priya', DATEADD('HOUR', -4, CURRENT_TIMESTAMP)),

('rev_upd_02', 'upd_02', 1,
 'Mastek Pulse 4-Week Pilot Kicks Off Across 3 Regions',
 '60 volunteers and 12 hosts across India, UK, and US are testing low-friction colleague connections.',
 'Participation is completely optional. Over the next four weeks we are measuring whether specific, capacity-capped invitations help colleagues find willing expertise beyond their immediate team.',
 'culture,team learning,inclusion', 'People & Culture Desk', 'https://intranet.mastek.demo/updates/pulse-pilot', FALSE, NULL, NULL,
 'usr_marcus', DATEADD('DAY', -1, CURRENT_TIMESTAMP)),

('rev_upd_03', 'upd_03', 1,
 'WCAG 2.1 AA Accessibility Checklist for Web & Mobile Releases',
 'Keyboard navigation, screen-reader labels, captions, and transcripts are now release gates for all internal apps.',
 'Every audio clip on Mastek Pulse includes a readable transcript, and playback never starts automatically. Review the updated engineering verification guide.',
 'accessibility,security,delivery excellence', 'Platform Engineering Standards', 'https://intranet.mastek.demo/updates/wcag-checklist', FALSE, NULL, NULL,
 'usr_priya', DATEADD('DAY', -2, CURRENT_TIMESTAMP)),

('rev_upd_04', 'upd_04', 1,
 'Cloud Migration Playbook v2.4 Published',
 'Updated database row-level security templates and zero-downtime PostgreSQL cutover checklists.',
 'The Cloud Practice has published reusable Terraform and Flyway templates featuring least-privilege application roles and transactional outbox tables.',
 'cloud architecture,innovation,security', 'Global Cloud Practice', 'https://intranet.mastek.demo/updates/cloud-playbook', FALSE, NULL, NULL,
 'usr_priya', DATEADD('DAY', -3, CURRENT_TIMESTAMP)),

('rev_upd_05', 'upd_05', 1,
 'Q4 Healthcare Delivery Highlights & Client Feedback',
 'How cross-functional pairing reduced incident triage times by 28% across UK & India pods.',
 'Read the brief on how structured handover notes between UK and India shifts improved release confidence.',
 'customer stories,delivery excellence', 'UK & India Delivery Office', 'https://intranet.mastek.demo/updates/q4-healthcare', FALSE, NULL, NULL,
 'usr_priya', DATEADD('DAY', -4, CURRENT_TIMESTAMP)),

('rev_upd_06', 'upd_06', 1,
 'Innovation highlights: AI-Assisted Documentation Patterns',
 'Company update in reviewqueue demonstrating Reviewer workflow (Page 11).',
 'Summary of how teams use contract-first OpenAPI specs and markdown guardrails to align Claude Code, Codex, and GitHub Copilot.',
 'innovation,ai engineering,team learning', 'Office of the CTO', 'https://intranet.mastek.demo/updates/ai-dlc', FALSE, NULL, NULL,
 NULL, NULL);

-- ============================================================================
-- 6. TWELVE SYNTHETIC EMPLOYEE CONTRIBUTIONS / STORIES (Pages 2, 3, 6, 7, 8, 10)
-- ============================================================================
INSERT INTO content_items (id, author_id, audience_id, content_type, status, current_version, published_version, published_at) VALUES
('story_01', 'usr_daniel', 'aud_pilot', 'STORY', 'PUBLISHED', 1, 1, DATEADD('HOUR', -5, CURRENT_TIMESTAMP)),
('story_02', 'usr_rohan', 'aud_pilot', 'STORY', 'PUBLISHED', 1, 1, DATEADD('DAY', -1, CURRENT_TIMESTAMP)),
('story_03', 'usr_sophie', 'aud_pilot', 'STORY', 'PUBLISHED', 1, 1, DATEADD('DAY', -2, CURRENT_TIMESTAMP)),
('story_04', 'usr_carlos', 'aud_pilot', 'STORY', 'PUBLISHED', 1, 1, DATEADD('DAY', -2, CURRENT_TIMESTAMP)),
('story_05', 'usr_priya', 'aud_pilot', 'STORY', 'PUBLISHED', 1, 1, DATEADD('DAY', -3, CURRENT_TIMESTAMP)),
('story_06', 'usr_elena', 'aud_pilot', 'STORY', 'PUBLISHED', 1, 1, DATEADD('DAY', -3, CURRENT_TIMESTAMP)),
('story_07', 'usr_asha', 'aud_pilot', 'STORY', 'PUBLISHED', 1, 1, DATEADD('DAY', -4, CURRENT_TIMESTAMP)),
('story_08', 'usr_daniel', 'aud_uk_restricted', 'STORY', 'PUBLISHED', 1, 1, DATEADD('DAY', -4, CURRENT_TIMESTAMP)),
('story_09', 'usr_rohan', 'aud_pilot', 'STORY', 'PUBLISHED', 1, 1, DATEADD('DAY', -5, CURRENT_TIMESTAMP)),
('story_10', 'usr_sophie', 'aud_pilot', 'STORY', 'APPROVED', 1, NULL, NULL),
('story_11', 'usr_carlos', 'aud_pilot', 'STORY', 'IN_REVIEW', 1, NULL, NULL),
('story_12', 'usr_asha', 'aud_pilot', 'STORY', 'DRAFT', 1, NULL, NULL);

INSERT INTO content_revisions (id, content_id, version_number, title, summary, body_text, tags_csv, has_audio, media_asset_key, duration_seconds, transcript_text, approved_by, approved_at) VALUES
-- STORY 01: Daniel's Flagship Retrospective Story (Exact copy from Wireframes 01 & 02 on Page 6!)
('rev_story_01', 'story_01', 1,
 'A retrospective everyone can contribute to',
 'One small change helped quieter voices join our team retrospective.',
 'We asked everyone to write first, then share. During our UK-India sprint retrospectives, we noticed that the loudest or most senior voices often set the anchor in the first two minutes.\n\nSo we tried one small change: we spent the first four minutes in silent writing where everyone added two concrete observations before anyone spoke out loud. We also rotated who facilitated each prompt.\n\nThis gave quieter colleagues a comfortable way to contribute, surfaced two deployment bottlenecks we had missed for months, and kept our retrospective under 30 minutes.',
 'team learning,delivery excellence,culture',
 TRUE, 'audio/story-01-daniel-retro.mp3', 95,
 'Daniel Wright (Delivery, UK): We asked everyone to write first, then share. This gave quieter colleagues a comfortable way to contribute and surfaced practical improvements across our distributed team.',
 'usr_priya', DATEADD('HOUR', -6, CURRENT_TIMESTAMP)),

-- STORY 02: Rohan on Java 21 Virtual Threads & PostgreSQL Row Locks
('rev_story_02', 'story_02', 1,
 'Cutting API tail latency with Java 21 Virtual Threads',
 'What we learned migrating a connection-heavy Spring Boot service without rewriting to reactive.',
 'When our morning traffic spiked, thread-pool exhaustion caused queueing delays. Enabling Java 21 virtual threads alongside carefully scoped PostgreSQL row locks dropped p95 latency by 42% while keeping our code simple enough for new interns to debug.',
 'innovation,cloud architecture,team learning',
 FALSE, NULL, NULL, NULL,
 'usr_priya', DATEADD('DAY', -1, CURRENT_TIMESTAMP)),

-- STORY 03: Sophie on Accessible Forms
('rev_story_03', 'story_03', 1,
 'Three screen-reader bugs we caught in 20 minutes',
 'Testing our clinical intake flow with VoiceOver and TalkBack before sprint sign-off.',
 'Instead of waiting for an external audit, our developers ran a 20-minute keyboard-only and VoiceOver drill every Wednesday. Happy to walk any team through our 5-point accessibility checklist.',
 'accessibility,delivery excellence,customer stories',
 FALSE, NULL, NULL, NULL,
 'usr_priya', DATEADD('DAY', -2, CURRENT_TIMESTAMP)),

-- STORY 04: Carlos on Evaluating AI Faithfulness
('rev_story_04', 'story_04', 1,
 'How we built a 30-case evaluation gate for internal AI helpers',
 'Testing permission boundaries, refusal behavior, and faithfulness before enabling AI drafting.',
 'Before turning on optional AI summarization, we created 30 synthetic test cases covering unauthorized audience leakage, hallucinated numbers, and template fallback behavior. Zero unauthorized disclosures is our non-negotiable release gate.',
 'innovation,ai engineering,security',
 FALSE, NULL, NULL, NULL,
 'usr_priya', DATEADD('DAY', -2, CURRENT_TIMESTAMP)),

-- STORY 05: Priya on Cross-Region Buddy Pairing
('rev_story_05', 'story_05', 1,
 'Making cross-office coffee chats less awkward',
 'Why "Ask me about this specific topic for 15 minutes" works better than open-ended networking.',
 'In our baseline interviews, employees told us they wanted to meet colleagues in other regions but felt awkward sending an unstructured cold message. Attaching a 15-minute topic prompt to a concrete story removes the guesswork.',
 'culture,inclusion,onboarding',
 FALSE, NULL, NULL, NULL,
 'usr_marcus', DATEADD('DAY', -3, CURRENT_TIMESTAMP)),

-- STORY 06: Elena on PostgreSQL Row-Level Security
('rev_story_06', 'story_06', 1,
 'Testing denied access in PostgreSQL row policies',
 'Why database owners can accidentally bypass RLS—and how we test least-privilege roles in CI.',
 'PostgreSQL table owners and roles with BYPASSRLS ignore row security policies by default. We added automated integration tests that run as a non-privileged application role and assert 403 Forbidden on restricted audiences.',
 'security,cloud architecture,delivery excellence',
 FALSE, NULL, NULL, NULL,
 'usr_priya', DATEADD('DAY', -3, CURRENT_TIMESTAMP)),

-- STORY 07: Asha on Contract-First TypeScript + Spring Boot
('rev_story_07', 'story_07', 1,
 'Keeping React, React Native, and Spring Boot in sync with OpenAPI',
 'How a single OpenAPI contract stopped frontend-backend integration bugs during our sprint.',
 'When three developers worked in different AI coding tools, agreeing on the OpenAPI schema and state machine upfront meant our mobile and web screens connected to the Java backend on the first try.',
 'team learning,innovation',
 FALSE, NULL, NULL, NULL,
 'usr_priya', DATEADD('DAY', -4, CURRENT_TIMESTAMP)),

-- STORY 08: Restricted UK Governance Story (Tests Audience Filtering!)
('rev_story_08', 'story_08', 1,
 'UK Delivery Commercial Risk Register Template',
 'Scoped exclusively to the UK Delivery Governance audience (`aud_uk_restricted`).',
 'This story is restricted to members of `aud_uk_restricted` (Daniel and Elena). Asha (`usr_asha`) must NEVER see this item in her feed or search results.',
 'delivery excellence,security',
 FALSE, NULL, NULL, NULL,
 'usr_priya', DATEADD('DAY', -4, CURRENT_TIMESTAMP)),

-- STORY 09: Rohan on Full-Capacity Invitation Demo
('rev_story_09', 'story_09', 1,
 'Zero-downtime Flyway migrations for 10,000+ user tables',
 'How to add columns and constraints without locking production traffic.',
 'We split expand-and-contract migrations into separate releases so active readers never block. (Note: The invitation on this story is already at full capacity to demonstrate the disabled Full Capacity UI state!)',
 'cloud architecture,delivery excellence',
 FALSE, NULL, NULL, NULL,
 'usr_priya', DATEADD('DAY', -5, CURRENT_TIMESTAMP)),

-- STORY 10: Approved & Ready for Publisher (Marcus) in Portal
('rev_story_10', 'story_10', 1,
 'Structured handover notes between UK and India shifts',
 'Approved by Reviewer Priya Nair; ready for Publisher Marcus Vance to release or schedule.',
 'A simple 4-bullet markdown template at the end of the UK afternoon reduced morning blocker messages for the Pune engineering team.',
 'delivery excellence,team learning',
 FALSE, NULL, NULL, NULL,
 'usr_priya', DATEADD('HOUR', -2, CURRENT_TIMESTAMP)),

-- STORY 11: In Review in Publishing Portal
('rev_story_11', 'story_11', 1,
 'Prompt engineering guardrails for multi-agent teams',
 'Submitted by Carlos Mendez; currently IN_REVIEW in the Publishing Portal.',
 'How our team uses CLAUDE.md, AGENTS.md, and .github/copilot-instructions.md pointing to a shared docs/aidlc/ specification hub.',
 'ai engineering,innovation,team learning',
 FALSE, NULL, NULL, NULL,
 NULL, NULL),

-- STORY 12: Asha's Local/Server Draft (Wireframe 05 - Page 8)
('rev_story_12', 'story_12', 1,
 'A better retrospective',
 'Draft saved by Asha Patel — ready to edit and submit for review.',
 'What changed? After chatting with Daniel, we tried five minutes of silent note-taking at the start of our sprint retro in Pune. What did we learn? Three junior developers shared test automation ideas that saved us two hours every release.',
 'team learning,culture',
 FALSE, NULL, NULL, NULL,
 NULL, NULL);

-- ============================================================================
-- 7. INVITATIONS ON STORIES (Open, Full, and Expired States for Wireframe Testing)
-- ============================================================================
INSERT INTO invitations (id, story_id, host_id, topic_label, duration_minutes, capacity, accepted_count, paused, expires_at) VALUES
-- Daniel's flagship offer: 15 minutes / 2 places this week (OPEN)
('inv_story_01', 'story_01', 'usr_daniel', 'Ask me about this: inclusive team retrospectives', 15, 2, 0, FALSE, DATEADD('DAY', 7, CURRENT_TIMESTAMP)),
-- Rohan's Java 21 offer: 15 minutes / 2 places (1 already accepted -> 1 place remaining)
('inv_story_02', 'story_02', 'usr_rohan', 'Ask me about this: Java 21 virtual threads in Spring Boot', 15, 2, 1, FALSE, DATEADD('DAY', 7, CURRENT_TIMESTAMP)),
-- Sophie's Accessibility offer: 15 minutes / 2 places (OPEN)
('inv_story_03', 'story_03', 'usr_sophie', 'Ask me about this: 20-minute WCAG screen-reader checks', 15, 2, 0, FALSE, DATEADD('DAY', 7, CURRENT_TIMESTAMP)),
-- Carlos's AI Evaluation offer: 15 minutes / 2 places (OPEN)
('inv_story_04', 'story_04', 'usr_carlos', 'Ask me about this: 30-case AI evaluation harness', 15, 2, 0, FALSE, DATEADD('DAY', 7, CURRENT_TIMESTAMP)),
-- Priya's Onboarding offer: 15 minutes / 3 places (OPEN)
('inv_story_05', 'story_05', 'usr_priya', 'Ask me about this: cross-office onboarding buddies', 15, 3, 1, FALSE, DATEADD('DAY', 7, CURRENT_TIMESTAMP)),
-- Rohan's Flyway story offer: FULL CAPACITY (capacity=2, accepted_count=2) to demonstrate disabled state!
('inv_story_09', 'story_09', 'usr_rohan', 'Ask me about this: zero-downtime Flyway migrations', 15, 2, 2, FALSE, DATEADD('DAY', 5, CURRENT_TIMESTAMP));

-- ============================================================================
-- 8. SEEDED CONNECTION REQUESTS & BILATERAL CONFIRMATIONS
-- ============================================================================
INSERT INTO connection_requests (id, invitation_id, requester_id, message_text, ai_assisted, idempotency_key, state, version, created_at) VALUES
-- An already ACCEPTED connection between Asha and Priya to showcase bilateral confirmation metrics
('req_seed_01', 'inv_story_05', 'usr_asha', 'Hi Priya, I would love to learn how your onboarding buddy prompts helped new engineers in Pune.', FALSE, 'idem-seed-001', 'ACCEPTED', 2, DATEADD('DAY', -2, CURRENT_TIMESTAMP)),
-- An already ACCEPTED connection between Daniel and Rohan
('req_seed_02', 'inv_story_02', 'usr_daniel', 'Hi Rohan, keen to compare notes on virtual thread pinning with PostgreSQL JDBC locks.', TRUE, 'idem-seed-002', 'ACCEPTED', 2, DATEADD('DAY', -1, CURRENT_TIMESTAMP));

-- Both Asha and Priya confirmed happened='YES' and useful=TRUE on req_seed_01 -> Counts as 1 verified Useful Connection!
INSERT INTO participant_confirmations (id, request_id, user_id, happened, useful) VALUES
('conf_seed_01a', 'req_seed_01', 'usr_asha', 'YES', TRUE),
('conf_seed_01b', 'req_seed_01', 'usr_priya', 'YES', TRUE),
-- On req_seed_02, only Daniel has confirmed so far; Rohan hasn't -> Outcome stays UNKNOWN until both confirm!
('conf_seed_02a', 'req_seed_02', 'usr_daniel', 'YES', TRUE);

-- ============================================================================
-- 9. SEEDED PLAYBACK PROGRESS (Matches Page 9 & 10 Wireframes: Culture Cast at 04:12 / 02:10)
-- ============================================================================
INSERT INTO playback_progress (user_id, content_id, position_seconds, duration_seconds) VALUES
('usr_asha', 'cc_ep_03', 130, 480),
('usr_daniel', 'rad_commute_01', 180, 720);

-- ============================================================================
-- 10. SEEDED AI USAGE LEDGER & AUDIT LOG
-- ============================================================================
INSERT INTO ai_usage_ledger (id, user_id, assist_type, context_id, input_tokens, output_tokens) VALUES
('ai_log_01', 'usr_daniel', 'INVITATION_DRAFT', 'story_02', 950, 420),
('ai_log_02', 'usr_asha', 'UPDATE_SUMMARY', 'upd_01', 1100, 480),
('ai_log_03', 'usr_carlos', 'STORY_POLISH', 'story_11', 1050, 510);

INSERT INTO audit_events (id, actor_id, action_type, target_type, target_id, details) VALUES
('aud_ev_01', 'usr_priya', 'REVISION_APPROVED', 'CONTENT_REVISION', 'rev_story_01', 'Reviewer Priya Nair approved Revision 1 of story_01 (Author: usr_daniel). Audience: aud_pilot.'),
('aud_ev_02', 'usr_marcus', 'CONTENT_PUBLISHED', 'CONTENT_ITEM', 'story_01', 'Publisher Marcus Vance released Revision 1 of story_01 to aud_pilot.'),
('aud_ev_03', 'usr_priya', 'REVISION_APPROVED', 'CONTENT_REVISION', 'rev_story_10', 'Reviewer Priya Nair approved Revision 1 of story_10 (Author: usr_sophie). Ready for Publisher release.');
