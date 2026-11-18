/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: web/src/api/personas.ts
 * Journey: Epic 8 (`TASK-801`) — Pre-Saved Demo Login Credentials & Rich
 *          Persona Portrait Photos across Web and Publishing Portal.
 *
 * WHY IT IS DESIGNED THIS WAY:
 *   1. Self-Contained SVG Data-URI Portraits:
 *      Instead of relying on external stock-photo CDNs that can fail on
 *      corporate firewalls or offline hackathon Wi-Fi, each persona has a
 *      high-contrast, custom-illustrated SVG portrait encoded as a `data:image/svg+xml`
 *      URI. It renders in 0ms in every browser and never breaks.
 *   2. Pre-Stored Demo Credentials (`DEMO_PASSWORD = 'Campfire@2026!'`):
 *      Lets evaluators and judges experience a realistic corporate SSO /
 *      credentials login screen with the password already pre-filled, then
 *      launch directly into any persona (`Asha`, `Daniel`, `Priya`, `Marcus`,
 *      `Elena`, `Rohan`).
 * ============================================================================
 */

export const DEMO_PASSWORD = 'Campfire@2026!';

export interface DemoPersonaMeta {
  id: string;
  displayName: string;
  email: string;
  jobTitle: string;
  functionName: string;
  region: string;
  roles: string[];
  bio: string;
  avatarUrl: string;
  badgeColor: string;
}

/**
 * Generates a rich, illustrated portrait SVG data URI so every persona and
 * story author has a crisp, distinct profile photo even when offline.
 */
function buildPortraitSvgDataUri(opts: {
  bgStart: string;
  bgEnd: string;
  skin: string;
  hair: string;
  shirt: string;
  collar: string;
  hairStyle: 'long' | 'short-part' | 'bun' | 'curly' | 'bob' | 'wave';
  glasses?: boolean;
  beard?: boolean;
  earrings?: boolean;
}): string {
  const hairBack =
    opts.hairStyle === 'long'
      ? `<path d="M28 52 C24 85, 30 108, 36 116 L84 116 C90 108, 96 85, 92 52 Z" fill="${opts.hair}"/>`
      : opts.hairStyle === 'bob'
      ? `<path d="M30 50 C26 78, 32 96, 38 100 L82 100 C88 96, 94 78, 90 50 Z" fill="${opts.hair}"/>`
      : opts.hairStyle === 'bun'
      ? `<circle cx="60" cy="20" r="13" fill="${opts.hair}"/>`
      : '';

  const hairFront =
    opts.hairStyle === 'long' || opts.hairStyle === 'bun'
      ? `<path d="M32 50 C32 28, 88 28, 88 50 C82 38, 66 34, 60 38 C52 34, 38 38, 32 50 Z" fill="${opts.hair}"/>`
      : opts.hairStyle === 'bob'
      ? `<path d="M31 52 C31 27, 89 27, 89 52 C80 36, 52 35, 31 52 Z" fill="${opts.hair}"/>`
      : opts.hairStyle === 'curly'
      ? `<path d="M32 48 C30 26, 90 26, 88 48 C84 36, 36 36, 32 48 Z" fill="${opts.hair}"/>
         <circle cx="38" cy="34" r="7" fill="${opts.hair}"/>
         <circle cx="52" cy="29" r="8" fill="${opts.hair}"/>
         <circle cx="68" cy="29" r="8" fill="${opts.hair}"/>
         <circle cx="81" cy="34" r="7" fill="${opts.hair}"/>`
      : `<path d="M33 49 C33 27, 87 27, 87 49 C78 36, 48 35, 33 49 Z" fill="${opts.hair}"/>`;

  const glassesSvg = opts.glasses
    ? `<g stroke="#1e293b" stroke-width="2" fill="none">
         <rect x="41" y="51" width="15" height="11" rx="3" fill="#ffffff" fill-opacity="0.18"/>
         <rect x="64" y="51" width="15" height="11" rx="3" fill="#ffffff" fill-opacity="0.18"/>
         <line x1="56" y1="56" x2="64" y2="56"/>
       </g>`
    : '';

  const beardSvg = opts.beard
    ? `<path d="M40 68 C41 82, 50 88, 60 88 C70 88, 79 82, 80 68 C76 74, 68 77, 60 77 C52 77, 44 74, 40 68 Z" fill="${opts.hair}" fill-opacity="0.88"/>`
    : '';

  const earringsSvg = opts.earrings
    ? `<circle cx="33" cy="65" r="2.3" fill="#fbbf24"/><circle cx="87" cy="65" r="2.3" fill="#fbbf24"/>`
    : '';

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" width="120" height="120">
    <defs>
      <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stop-color="${opts.bgStart}"/>
        <stop offset="100%" stop-color="${opts.bgEnd}"/>
      </linearGradient>
      <clipPath id="circleClip">
        <circle cx="60" cy="60" r="60"/>
      </clipPath>
    </defs>
    <g clip-path="url(#circleClip)">
      <rect width="120" height="120" fill="url(#bg)"/>
      <circle cx="98" cy="22" r="28" fill="#ffffff" fill-opacity="0.08"/>
      ${hairBack}
      <!-- Shoulders / Blazer -->
      <path d="M16 122 C18 94, 38 86, 60 86 C82 86, 102 94, 104 122 Z" fill="${opts.shirt}"/>
      <path d="M44 87 L60 108 L76 87 Z" fill="${opts.collar}"/>
      <!-- Neck -->
      <rect x="51" y="74" width="18" height="18" rx="6" fill="${opts.skin}"/>
      <!-- Ears -->
      <circle cx="33" cy="60" r="5" fill="${opts.skin}"/>
      <circle cx="87" cy="60" r="5" fill="${opts.skin}"/>
      ${earringsSvg}
      <!-- Head -->
      <rect x="35" y="32" width="50" height="53" rx="24" fill="${opts.skin}"/>
      ${beardSvg}
      <!-- Eyes -->
      <circle cx="48.5" cy="56" r="2.6" fill="#0f172a"/>
      <circle cx="71.5" cy="56" r="2.6" fill="#0f172a"/>
      <circle cx="49.3" cy="55.2" r="0.8" fill="#ffffff"/>
      <circle cx="72.3" cy="55.2" r="0.8" fill="#ffffff"/>
      <!-- Eyebrows -->
      <path d="M43 49 Q48.5 46.5 54 49" stroke="${opts.hair}" stroke-width="2.2" fill="none" stroke-linecap="round"/>
      <path d="M66 49 Q71.5 46.5 77 49" stroke="${opts.hair}" stroke-width="2.2" fill="none" stroke-linecap="round"/>
      <!-- Nose -->
      <path d="M60 56 L58.5 64 L62 64.5" stroke="#b45309" stroke-opacity="0.45" stroke-width="1.8" fill="none" stroke-linecap="round"/>
      <!-- Warm Smile -->
      <path d="M51 71 Q60 77 69 71" stroke="#991b1b" stroke-width="2.4" fill="none" stroke-linecap="round"/>
      ${glassesSvg}
      ${hairFront}
    </g>
  </svg>`;

  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`;
}

export const DEMO_PERSONAS: Record<string, DemoPersonaMeta> = {
  usr_asha: {
    id: 'usr_asha',
    displayName: 'Asha Patel',
    email: 'asha.patel@mastek.com',
    jobTitle: 'Senior Full-Stack Engineer',
    functionName: 'Engineering',
    region: 'INDIA',
    roles: ['EMPLOYEE', 'CREATOR'],
    bio: 'Builds cloud-native delivery pipelines in Pune. Passionate about sprint pairing, test automation, and inclusive team retrospectives.',
    badgeColor: 'bg-blue-100 text-[#002180] border-blue-300',
    avatarUrl: buildPortraitSvgDataUri({
      bgStart: '#002180',
      bgEnd: '#2563eb',
      skin: '#d99b72',
      hair: '#1e1b18',
      shirt: '#0f766e',
      collar: '#f8fafc',
      hairStyle: 'long',
      earrings: true,
    }),
  },
  usr_daniel: {
    id: 'usr_daniel',
    displayName: 'Daniel Wright',
    email: 'daniel.wright@mastek.com',
    jobTitle: 'Principal Delivery Lead',
    functionName: 'Delivery',
    region: 'UK',
    roles: ['EMPLOYEE', 'CREATOR'],
    bio: 'Leads NHS & UK public-sector delivery squads in Leeds. Hosts 15-minute Campfire chats on psychological safety and agile ceremonies.',
    badgeColor: 'bg-indigo-100 text-indigo-900 border-indigo-300',
    avatarUrl: buildPortraitSvgDataUri({
      bgStart: '#1e3a8a',
      bgEnd: '#0284c7',
      skin: '#f1c29b',
      hair: '#5c3a21',
      shirt: '#1e293b',
      collar: '#e2e8f0',
      hairStyle: 'short-part',
      glasses: true,
      beard: true,
    }),
  },
  usr_priya: {
    id: 'usr_priya',
    displayName: 'Priya Nair',
    email: 'priya.nair@mastek.com',
    jobTitle: 'Engineering Practice Reviewer',
    functionName: 'People & Culture',
    region: 'INDIA',
    roles: ['EMPLOYEE', 'CREATOR', 'REVIEWER'],
    bio: 'Stewards editorial quality and revision accuracy in the Mastek Campfire Publishing Portal across India and Global pilot cohorts.',
    badgeColor: 'bg-purple-100 text-purple-900 border-purple-300',
    avatarUrl: buildPortraitSvgDataUri({
      bgStart: '#4c1d95',
      bgEnd: '#7c3aed',
      skin: '#c98860',
      hair: '#171717',
      shirt: '#9333ea',
      collar: '#fef3c7',
      hairStyle: 'bun',
      glasses: true,
      earrings: true,
    }),
  },
  usr_marcus: {
    id: 'usr_marcus',
    displayName: 'Marcus Vance',
    email: 'marcus.vance@mastek.com',
    jobTitle: 'Global Internal Comms Publisher',
    functionName: 'Communications',
    region: 'US',
    roles: ['EMPLOYEE', 'PUBLISHER', 'MODERATOR'],
    bio: 'Curates Mastek Radio, Culture Cast episodes, and weekly Campfire challenge publications from Dallas.',
    badgeColor: 'bg-emerald-100 text-emerald-900 border-emerald-300',
    avatarUrl: buildPortraitSvgDataUri({
      bgStart: '#065f46',
      bgEnd: '#10b981',
      skin: '#8d5524',
      hair: '#111827',
      shirt: '#0f172a',
      collar: '#34d399',
      hairStyle: 'curly',
      beard: true,
    }),
  },
  usr_elena: {
    id: 'usr_elena',
    displayName: 'Elena Rostova',
    email: 'elena.rostova@mastek.com',
    jobTitle: 'Cloud Platform Architect & Admin',
    functionName: 'Architecture',
    region: 'UK',
    roles: ['EMPLOYEE', 'CREATOR', 'ADMIN'],
    bio: 'Oversees platform trust boundaries, AI budget guardrails, and audience scoping for the UK and Global engineering teams.',
    badgeColor: 'bg-amber-100 text-amber-900 border-amber-300',
    avatarUrl: buildPortraitSvgDataUri({
      bgStart: '#78350f',
      bgEnd: '#d97706',
      skin: '#f5cfa8',
      hair: '#92400e',
      shirt: '#1e3a8a',
      collar: '#ffffff',
      hairStyle: 'bob',
      earrings: true,
    }),
  },
  usr_rohan: {
    id: 'usr_rohan',
    displayName: 'Rohan Deshmukh',
    email: 'rohan.deshmukh@mastek.com',
    jobTitle: 'Customer Success Lead',
    functionName: 'Customer Success',
    region: 'INDIA',
    roles: ['EMPLOYEE', 'CREATOR'],
    bio: 'Partners with enterprise clients in Mumbai and connects cross-regional delivery teams through Campfire stories.',
    badgeColor: 'bg-sky-100 text-sky-900 border-sky-300',
    avatarUrl: buildPortraitSvgDataUri({
      bgStart: '#0c4a6e',
      bgEnd: '#0284c7',
      skin: '#d29166',
      hair: '#1f2937',
      shirt: '#0369a1',
      collar: '#f0f9ff',
      hairStyle: 'wave',
    }),
  },
};

/**
 * Resolves the portrait photo URL for any user ID or display name.
 * Always returns a valid SVG data URI portrait so images never break.
 */
export function getPersonaAvatar(userIdOrName?: string | null): string {
  if (!userIdOrName) {
    return DEMO_PERSONAS.usr_asha.avatarUrl;
  }
  if (DEMO_PERSONAS[userIdOrName]) {
    return DEMO_PERSONAS[userIdOrName].avatarUrl;
  }
  const lower = userIdOrName.toLowerCase();
  for (const meta of Object.values(DEMO_PERSONAS)) {
    if (meta.displayName.toLowerCase().includes(lower) || lower.includes(meta.displayName.split(' ')[0].toLowerCase())) {
      return meta.avatarUrl;
    }
  }
  return DEMO_PERSONAS.usr_daniel.avatarUrl;
}

/**
 * Resolves full demo metadata (`email`, `jobTitle`, `bio`, `avatarUrl`) for a user ID.
 */
export function getPersonaMeta(userId?: string | null): DemoPersonaMeta {
  if (userId && DEMO_PERSONAS[userId]) {
    return DEMO_PERSONAS[userId];
  }
  return DEMO_PERSONAS.usr_asha;
}
