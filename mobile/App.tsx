import React, { useState } from 'react';
import {
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: mobile/App.tsx
 * Journey: Android & iOS Native Mobile App (React Native + Expo + TypeScript)
 *          implementing all 8 Mobile Wireframes from Proposal Pages 6, 7, 8, 9:
 *   - 01 / Your Pulse (Discover & Factual `WHY THIS?` badge)
 *   - 02 / Story Detail (`Ask me about this` & Transcript toggle)
 *   - 03 / Invitation Composer (`Suggest a draft with AI` + Template Fallback)
 *   - 04 / Host Request Inbox (`Accept` / `Decline` with 1-Transaction lock)
 *   - 05 / Publish (`Draft saved on this device` + Exact Revision Preview)
 *   - 06 / Private Follow-Up (`Did the conversation happen?` + `Was it useful?`)
 *   - 07 / Mastek Radio (`Your Commute Edition` + Up Next Queue)
 *   - 08 / Culture Cast (`02:10 ---- 08:00`, Back 15s / Forward 15s, Chapters,
 *          Transcript, and `Explore the related story`)
 *   - Bottom 5-Tab Bar: `Pulse`, `Listen`, `Connect`, `Publish`, `Profile`
 * ============================================================================
 */

type BottomTab = 'PULSE' | 'LISTEN' | 'CONNECT' | 'PUBLISH' | 'PROFILE';

export default function App() {
  const [activeTab, setActiveTab] = useState<BottomTab>('PULSE');
  const [persona, setPersona] = useState<'usr_asha' | 'usr_daniel'>('usr_asha');

  // Primary Journey State (Asha -> Daniel -> Accept -> Confirm)
  const [pulseSubScreen, setPulseSubScreen] = useState<'FEED' | 'STORY' | 'INVITE'>('FEED');
  const [inviteMessage, setInviteMessage] = useState(
    'Hi Daniel, I would love to learn how you made retrospectives more inclusive. Could we compare notes?'
  );
  const [remainingPlaces, setRemainingPlaces] = useState<number>(2);
  const [requestState, setRequestState] = useState<'NONE' | 'PENDING' | 'ACCEPTED' | 'DECLINED'>('NONE');
  const [ashaConfirmed, setAshaConfirmed] = useState<boolean>(false);
  const [danielConfirmed, setDanielConfirmed] = useState<boolean>(false);
  const [showCheckInScreen, setShowCheckInScreen] = useState<boolean>(false);
  const [happenedChoice, setHappenedChoice] = useState<'YES' | 'NO' | 'NOT_YET'>('YES');
  const [usefulChoice, setUsefulChoice] = useState<boolean>(true);

  // Listen Tab State (Wireframe 07 & 08)
  const [listenMode, setListenMode] = useState<'RADIO' | 'CULTURE_CAST'>('RADIO');
  const [castPositionSec, setCastPositionSec] = useState<number>(130); // 02:10

  // Publish Tab State (Wireframe 05)
  const [draftTitle, setDraftTitle] = useState('A better retrospective');
  const [draftStory, setDraftStory] = useState(
    'What changed? We started our retro with 4 minutes of silent writing. What did we learn? Quieter voices shared concrete test automation ideas right away.'
  );
  const [submittedRevision, setSubmittedRevision] = useState(false);

  const bilateralUseful = ashaConfirmed && danielConfirmed;

  return (
    <SafeAreaView style={styles.container}>
      {/* Top Status & Persona Switcher Bar */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Mastek Pulse</Text>
        <TouchableOpacity
          accessibilityRole="button"
          accessibilityLabel="Switch active demo persona between Asha and Daniel"
          onPress={() => setPersona(persona === 'usr_asha' ? 'usr_daniel' : 'usr_asha')}
          style={styles.personaPill}
        >
          <Text style={styles.personaPillText}>
            Persona: {persona === 'usr_asha' ? 'Asha (India)' : 'Daniel (UK Host)'} ⇄
          </Text>
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={styles.content}>
        {/* ===================================================================
            TAB 1: PULSE (Wireframes 01, 02, 03 - Pages 6 & 7)
           =================================================================== */}
        {activeTab === 'PULSE' && pulseSubScreen === 'FEED' && (
          <View style={styles.card}>
            <Text style={styles.screenHeading}>Your Pulse</Text>
            <Text style={styles.subText}>For you / Learning / Community</Text>

            <View style={styles.storyBox}>
              <Text style={styles.metaLabel}>D Daniel / Delivery / UK</Text>
              <Text style={styles.storyTitle}>A retrospective everyone can contribute to</Text>
              <Text style={styles.bodyCopy}>
                One small change helped quieter voices join our team retrospective.
              </Text>
              <View style={styles.whyBadge}>
                <Text style={styles.whyBadgeText}>
                  WHY THIS? Your interest: team learning · 15 min conversation / {remainingPlaces} places available
                </Text>
              </View>
              <TouchableOpacity
                style={styles.primaryBtn}
                onPress={() => setPulseSubScreen('STORY')}
              >
                <Text style={styles.primaryBtnText}>Read story &gt;</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}

        {activeTab === 'PULSE' && pulseSubScreen === 'STORY' && (
          <View style={styles.card}>
            <TouchableOpacity onPress={() => setPulseSubScreen('FEED')}>
              <Text style={styles.backLink}>&lt; Story</Text>
            </TouchableOpacity>
            <Text style={styles.metaLabel}>Daniel / Delivery / UK</Text>
            <Text style={styles.storyTitle}>A retrospective everyone can contribute to</Text>
            <Text style={styles.bodyCopy}>
              We asked everyone to write first, then share. This gave quieter colleagues a comfortable way to
              contribute.
            </Text>

            <View style={styles.infoBox}>
              <Text style={styles.metaLabel}>Optional audio [ play ] / Transcript</Text>
              <Text style={styles.smallText}>
                Transcript: "We asked everyone to write first, then share..."
              </Text>
            </View>

            <View style={styles.inviteOfferBox}>
              <Text style={styles.storyTitle}>Ask me about this</Text>
              <Text style={styles.bodyCopy}>
                15 minutes / {remainingPlaces} places this week{'\n'}Audience: colleagues in the pilot
              </Text>
              <TouchableOpacity
                style={styles.primaryBtn}
                onPress={() => setPulseSubScreen('INVITE')}
              >
                <Text style={styles.primaryBtnText}>Write an invitation</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}

        {activeTab === 'PULSE' && pulseSubScreen === 'INVITE' && (
          <View style={styles.card}>
            <TouchableOpacity onPress={() => setPulseSubScreen('STORY')}>
              <Text style={styles.backLink}>&lt; Invite Daniel</Text>
            </TouchableOpacity>
            <Text style={styles.storyTitle}>About: team retrospectives</Text>
            <Text style={styles.metaLabel}>YOUR MESSAGE</Text>
            <TextInput
              multiline
              style={styles.textArea}
              value={inviteMessage}
              onChangeText={setInviteMessage}
            />
            <TouchableOpacity
              style={styles.secondaryBtn}
              onPress={() =>
                setInviteMessage(
                  'Hi Daniel, I saw your retrospective story and would love to compare notes on silent writing prompts for 15 minutes this week. (AI-assisted draft)'
                )
              }
            >
              <Text style={styles.secondaryBtnText}>Suggest a draft with AI (or template fallback)</Text>
            </TouchableOpacity>
            <Text style={styles.smallText}>
              Review and edit before sending. Daniel can accept or decline.
            </Text>
            <TouchableOpacity
              style={styles.primaryBtn}
              onPress={() => {
                setRequestState('PENDING');
                setPulseSubScreen('FEED');
                setActiveTab('CONNECT');
              }}
            >
              <Text style={styles.primaryBtnText}>Send invitation</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* ===================================================================
            TAB 2: LISTEN (Wireframes 07 & 08 - Page 9)
           =================================================================== */}
        {activeTab === 'LISTEN' && (
          <View style={styles.card}>
            <Text style={styles.screenHeading}>Listen</Text>
            <View style={styles.row}>
              <TouchableOpacity
                style={[styles.tabPill, listenMode === 'RADIO' && styles.tabPillActive]}
                onPress={() => setListenMode('RADIO')}
              >
                <Text style={listenMode === 'RADIO' ? styles.tabPillTextActive : styles.tabPillText}>
                  07 / Mastek Radio
                </Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.tabPill, listenMode === 'CULTURE_CAST' && styles.tabPillActive]}
                onPress={() => setListenMode('CULTURE_CAST')}
              >
                <Text style={listenMode === 'CULTURE_CAST' ? styles.tabPillTextActive : styles.tabPillText}>
                  08 / Culture Cast
                </Text>
              </TouchableOpacity>
            </View>

            {listenMode === 'RADIO' ? (
              <View style={styles.storyBox}>
                <Text style={styles.metaLabel}>YOUR COMMUTE EDITION</Text>
                <Text style={styles.storyTitle}>A little closer to Mastek.</Text>
                <Text style={styles.bodyCopy}>
                  Company news + colleague stories · Curated queue / 12 min remaining
                </Text>
                <TouchableOpacity style={styles.primaryBtn}>
                  <Text style={styles.primaryBtnText}>Play Mastek Radio</Text>
                </TouchableOpacity>
                <Text style={[styles.metaLabel, { marginTop: 12 }]}>UP NEXT</Text>
                <Text style={styles.smallText}>01 Company pulse / 2 min</Text>
                <Text style={styles.smallText}>02 Innovation spotlight / 4 min</Text>
                <Text style={styles.smallText}>03 A story across borders / 6 min</Text>
              </View>
            ) : (
              <View style={styles.storyBox}>
                <Text style={styles.metaLabel}>ON DEMAND / EPISODE 04</Text>
                <Text style={styles.storyTitle}>Different places. Shared possibilities.</Text>
                <Text style={styles.bodyCopy}>
                  A conversation about ideas and culture across geographies and functions · 8 min
                </Text>
                <View style={styles.infoBox}>
                  <Text style={styles.metaLabel}>
                    0{Math.floor(castPositionSec / 60)}:
                    {String(castPositionSec % 60).padStart(2, '0')} -------------- 08:00
                  </Text>
                  <View style={styles.row}>
                    <TouchableOpacity
                      style={styles.secondaryBtn}
                      onPress={() => setCastPositionSec(Math.max(0, castPositionSec - 15))}
                    >
                      <Text style={styles.secondaryBtnText}>Back 15s</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={styles.secondaryBtn}
                      onPress={() => setCastPositionSec(Math.min(480, castPositionSec + 15))}
                    >
                      <Text style={styles.secondaryBtnText}>Forward 15s</Text>
                    </TouchableOpacity>
                  </View>
                </View>
                <TouchableOpacity
                  style={styles.primaryBtn}
                  onPress={() => {
                    setActiveTab('PULSE');
                    setPulseSubScreen('STORY');
                  }}
                >
                  <Text style={styles.primaryBtnText}>Explore the related story</Text>
                </TouchableOpacity>
              </View>
            )}
          </View>
        )}

        {/* ===================================================================
            TAB 3: CONNECT (Wireframes 04 & 06 - Pages 7 & 8)
           =================================================================== */}
        {activeTab === 'CONNECT' && !showCheckInScreen && (
          <View style={styles.card}>
            <Text style={styles.screenHeading}>Connections</Text>
            <Text style={styles.subText}>Received / Sent / Accepted</Text>

            {requestState === 'NONE' ? (
              <Text style={styles.bodyCopy}>
                No active requests yet. Open the Pulse tab to send an invitation to Daniel!
              </Text>
            ) : (
              <View style={styles.storyBox}>
                <Text style={styles.metaLabel}>{requestState} / From Asha → Daniel</Text>
                <Text style={styles.storyTitle}>About your retrospective story</Text>
                <Text style={styles.bodyCopy}>"{inviteMessage}"</Text>
                <Text style={styles.smallText}>{remainingPlaces} places remaining this week</Text>

                {requestState === 'PENDING' && (
                  <View style={styles.row}>
                    <TouchableOpacity
                      style={[styles.primaryBtn, { flex: 1, marginRight: 8 }]}
                      onPress={() => {
                        setRequestState('ACCEPTED');
                        setRemainingPlaces(Math.max(0, remainingPlaces - 1));
                      }}
                    >
                      <Text style={styles.primaryBtnText}>Accept (Lock Row)</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={[styles.secondaryBtn, { flex: 1 }]}
                      onPress={() => setRequestState('DECLINED')}
                    >
                      <Text style={styles.secondaryBtnText}>Decline</Text>
                    </TouchableOpacity>
                  </View>
                )}

                {requestState === 'ACCEPTED' && (
                  <View style={{ marginTop: 10 }}>
                    <Text style={styles.smallText}>
                      Bilateral Status:{' '}
                      {bilateralUseful
                        ? '✓ USEFUL CONNECTION CONFIRMED BY BOTH'
                        : `UNKNOWN (Asha: ${ashaConfirmed ? 'Yes' : 'Pending'}, Daniel: ${
                            danielConfirmed ? 'Yes' : 'Pending'
                          })`}
                    </Text>
                    <TouchableOpacity
                      style={styles.primaryBtn}
                      onPress={() => setShowCheckInScreen(true)}
                    >
                      <Text style={styles.primaryBtnText}>
                        Complete Private Check-in as {persona === 'usr_asha' ? 'Asha' : 'Daniel'}
                      </Text>
                    </TouchableOpacity>
                  </View>
                )}
              </View>
            )}
          </View>
        )}

        {activeTab === 'CONNECT' && showCheckInScreen && (
          <View style={styles.card}>
            <TouchableOpacity onPress={() => setShowCheckInScreen(false)}>
              <Text style={styles.backLink}>&lt; A quick check-in</Text>
            </TouchableOpacity>
            <Text style={styles.storyTitle}>
              Your conversation with {persona === 'usr_asha' ? 'Daniel' : 'Asha'}
            </Text>
            <Text style={styles.smallText}>
              Only you and {persona === 'usr_asha' ? 'Daniel' : 'Asha'} can access this feedback. You can skip it.
            </Text>

            <Text style={[styles.metaLabel, { marginTop: 12 }]}>Did the conversation happen?</Text>
            <View style={styles.row}>
              {(['YES', 'NO', 'NOT_YET'] as const).map((opt) => (
                <TouchableOpacity
                  key={opt}
                  style={[styles.tabPill, happenedChoice === opt && styles.tabPillActive]}
                  onPress={() => setHappenedChoice(opt)}
                >
                  <Text style={happenedChoice === opt ? styles.tabPillTextActive : styles.tabPillText}>
                    {opt}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            {happenedChoice === 'YES' && (
              <>
                <Text style={[styles.metaLabel, { marginTop: 12 }]}>Was it useful?</Text>
                <View style={styles.row}>
                  <TouchableOpacity
                    style={[styles.tabPill, usefulChoice && styles.tabPillActive]}
                    onPress={() => setUsefulChoice(true)}
                  >
                    <Text style={usefulChoice ? styles.tabPillTextActive : styles.tabPillText}>Yes</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.tabPill, !usefulChoice && styles.tabPillActive]}
                    onPress={() => setUsefulChoice(false)}
                  >
                    <Text style={!usefulChoice ? styles.tabPillTextActive : styles.tabPillText}>No</Text>
                  </TouchableOpacity>
                </View>
              </>
            )}

            <TouchableOpacity
              style={styles.primaryBtn}
              onPress={() => {
                if (happenedChoice === 'YES' && usefulChoice) {
                  if (persona === 'usr_asha') setAshaConfirmed(true);
                  else setDanielConfirmed(true);
                }
                setShowCheckInScreen(false);
              }}
            >
              <Text style={styles.primaryBtnText}>Save feedback</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* ===================================================================
            TAB 4: PUBLISH (Wireframe 05 - Page 8)
           =================================================================== */}
        {activeTab === 'PUBLISH' && (
          <View style={styles.card}>
            <Text style={styles.screenHeading}>Publish a story</Text>
            <Text style={styles.smallText}>Draft saved on this device</Text>
            <Text style={[styles.metaLabel, { marginTop: 10 }]}>STORY TITLE</Text>
            <TextInput style={styles.input} value={draftTitle} onChangeText={setDraftTitle} />
            <Text style={[styles.metaLabel, { marginTop: 10 }]}>YOUR STORY</Text>
            <TextInput
              multiline
              style={styles.textArea}
              value={draftStory}
              onChangeText={setDraftStory}
            />
            <View style={styles.infoBox}>
              <Text style={styles.smallText}>
                Audience: Pilot colleagues · Invitation: Ask me about this · Capacity: 2 / Expires: 7 days
              </Text>
            </View>
            <TouchableOpacity
              style={styles.primaryBtn}
              onPress={() => setSubmittedRevision(true)}
            >
              <Text style={styles.primaryBtnText}>Submit for review (Revision 1)</Text>
            </TouchableOpacity>
            {submittedRevision && (
              <Text style={[styles.whyBadgeText, { marginTop: 8 }]}>
                ✓ Revision 1 submitted to Publishing Portal Review Queue!
              </Text>
            )}
          </View>
        )}

        {/* ===================================================================
            TAB 5: PROFILE
           =================================================================== */}
        {activeTab === 'PROFILE' && (
          <View style={styles.card}>
            <Text style={styles.screenHeading}>Profile & Controls</Text>
            <Text style={styles.bodyCopy}>
              Active Persona: {persona === 'usr_asha' ? 'Asha Patel (Engineering, India)' : 'Daniel Wright (Delivery, UK)'}
            </Text>
            <Text style={styles.smallText}>
              Interests: team learning, innovation, culture{'\n'}
              Minimum collection: Opt-in fields only. No employee appraisal scores or public popularity rankings.
            </Text>
          </View>
        )}
      </ScrollView>

      {/* Bottom 5-Tab Navigation matching Wireframes 01–08 */}
      <View style={styles.bottomNav}>
        {(['PULSE', 'LISTEN', 'CONNECT', 'PUBLISH', 'PROFILE'] as const).map((tab) => (
          <TouchableOpacity
            key={tab}
            style={styles.navItem}
            onPress={() => setActiveTab(tab)}
          >
            <Text style={[styles.navText, activeTab === tab && styles.navTextActive]}>
              {tab.charAt(0) + tab.slice(1).toLowerCase()}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F8FAFC' },
  header: {
    backgroundColor: '#002180',
    paddingHorizontal: 16,
    paddingVertical: 14,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  headerTitle: { color: '#FFFFFF', fontSize: 18, fontWeight: '800' },
  personaPill: {
    backgroundColor: '#1E3A8A',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 16,
  },
  personaPillText: { color: '#DBEAFE', fontSize: 11, fontWeight: '700' },
  content: { padding: 16, paddingBottom: 80 },
  card: {
    backgroundColor: '#FFFFFF',
    borderRadius: 14,
    padding: 16,
    borderWidth: 1,
    borderColor: '#E2E8F0',
  },
  screenHeading: { fontSize: 22, fontWeight: '800', color: '#002180', marginBottom: 4 },
  subText: { fontSize: 12, color: '#64748B', marginBottom: 12 },
  storyBox: {
    marginTop: 8,
    padding: 14,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#CBD5E1',
    backgroundColor: '#F8FAFC',
  },
  metaLabel: { fontSize: 11, fontWeight: '700', color: '#475569', textTransform: 'uppercase' },
  storyTitle: { fontSize: 17, fontWeight: '800', color: '#0F172A', marginTop: 4 },
  bodyCopy: { fontSize: 13, color: '#334155', marginTop: 6, lineHeight: 19 },
  smallText: { fontSize: 12, color: '#475569', marginTop: 6 },
  whyBadge: {
    backgroundColor: '#EFF6FF',
    borderRadius: 8,
    padding: 8,
    marginTop: 10,
    borderWidth: 1,
    borderColor: '#BFDBFE',
  },
  whyBadgeText: { fontSize: 11, fontWeight: '700', color: '#002180' },
  primaryBtn: {
    backgroundColor: '#002180',
    borderRadius: 10,
    paddingVertical: 12,
    paddingHorizontal: 16,
    marginTop: 12,
    alignItems: 'center',
  },
  primaryBtnText: { color: '#FFFFFF', fontSize: 13, fontWeight: '700' },
  secondaryBtn: {
    backgroundColor: '#E2E8F0',
    borderRadius: 10,
    paddingVertical: 10,
    paddingHorizontal: 12,
    marginTop: 10,
    alignItems: 'center',
  },
  secondaryBtnText: { color: '#1E293B', fontSize: 12, fontWeight: '700' },
  backLink: { fontSize: 13, fontWeight: '700', color: '#002180', marginBottom: 10 },
  infoBox: {
    backgroundColor: '#F1F5F9',
    padding: 12,
    borderRadius: 10,
    marginTop: 12,
  },
  inviteOfferBox: {
    backgroundColor: '#EFF6FF',
    padding: 14,
    borderRadius: 10,
    marginTop: 14,
    borderWidth: 1,
    borderColor: '#BFDBFE',
  },
  input: {
    borderWidth: 1,
    borderColor: '#CBD5E1',
    borderRadius: 8,
    padding: 10,
    fontSize: 13,
    backgroundColor: '#FFFFFF',
    marginTop: 4,
  },
  textArea: {
    borderWidth: 1,
    borderColor: '#CBD5E1',
    borderRadius: 8,
    padding: 10,
    fontSize: 13,
    minHeight: 80,
    backgroundColor: '#FFFFFF',
    marginTop: 4,
  },
  row: { flexDirection: 'row', alignItems: 'center', marginTop: 8, gap: 8 },
  tabPill: {
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: 16,
    backgroundColor: '#E2E8F0',
    marginRight: 8,
  },
  tabPillActive: { backgroundColor: '#002180' },
  tabPillText: { fontSize: 12, fontWeight: '600', color: '#334155' },
  tabPillTextActive: { fontSize: 12, fontWeight: '700', color: '#FFFFFF' },
  bottomNav: {
    flexDirection: 'row',
    borderTopWidth: 1,
    borderTopColor: '#CBD5E1',
    backgroundColor: '#FFFFFF',
    paddingVertical: 10,
  },
  navItem: { flex: 1, alignItems: 'center' },
  navText: { fontSize: 12, color: '#64748B', fontWeight: '600' },
  navTextActive: { color: '#002180', fontWeight: '800' },
});
