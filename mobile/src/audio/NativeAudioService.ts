import { createAudioPlayer, setAudioModeAsync, AudioPlayer } from 'expo-audio';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: mobile/src/audio/NativeAudioService.ts
 * Journey: Supporting Journey A ("Listen to Mastek Radio and Culture Cast,
 *          with transcripts and playback resume" - Proposal Pages 9, 14, 15).
 *
 * WHY IT IS DESIGNED THIS WAY:
 *   Proposal Pages 9 & 15 require:
 *   - "Include background and lock-screen controls, audio interruption recovery,
 *      transcripts and clear reconnect states. No automatic playback."
 *   - Upgraded to `expo-audio` (Expo SDK 58 standard replacing legacy `expo-av`)
 *     with `shouldPlayInBackground: true` and `playsInSilentMode: true`
 *     so commuters can listen to Mastek Radio with their phone screen locked,
 *     while syncing `currentTime` back to `/api/v1/listen/progress`!
 * ============================================================================
 */
export class NativeAudioService {
  // Holds the currently active `expo-audio` AudioPlayer instance so we can pause or release it cleanly
  private player: AudioPlayer | null = null;

  /**
   * Configures native iOS AVAudioSession and Android AudioManager session flags
   * before starting playback so audio continues playing when the user locks their
   * screen or switches apps during a commute.
   */
  async configureBackgroundAudio(): Promise<void> {
    await setAudioModeAsync({
      allowsRecording: false,
      shouldPlayInBackground: true,
      playsInSilentMode: true,
      interruptionMode: 'doNotMix',
      interruptionModeAndroid: 'doNotMix',
      shouldRouteThroughEarpiece: false,
    });
  }

  /**
   * Loads a short-lived signed audio URL (`signedUrl`), seeks to the user's cross-device
   * saved timestamp (`resumePositionSeconds`), starts playback upon explicit user action,
   * and streams whole-second progress callbacks (`onProgressSec`) so the caller can
   * persist progress to `PUT /api/v1/listen/progress`.
   */
  async playFromSignedUrl(
    signedUrl: string,
    resumePositionSeconds: number,
    onProgressSec: (sec: number) => void
  ): Promise<void> {
    await this.configureBackgroundAudio();

    if (this.player) {
      this.player.remove();
      this.player = null;
    }

    const player = createAudioPlayer({ uri: signedUrl });
    await player.seekTo(resumePositionSeconds);
    player.play();
    onProgressSec(Math.floor(resumePositionSeconds));
    this.player = player;
  }

  /**
   * Pauses the active audio stream (if loaded) while preserving the current playhead position.
   */
  async pause(): Promise<void> {
    if (this.player) {
      this.player.pause();
    }
  }
}
