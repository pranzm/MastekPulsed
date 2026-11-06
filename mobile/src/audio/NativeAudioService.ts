import { Audio, InterruptionModeAndroid, InterruptionModeIOS } from 'expo-av';

/**
 * ============================================================================
 * INTERN & AI AGENT GUIDE — WHAT IS THIS FILE?
 * ============================================================================
 * File: mobile/src/audio/NativeAudioService.ts
 * Journey: Supporting Journey A ("Listen to Mastek Radio and Culture Cast,
 *          with transcripts and playback resume" - Proposal Pages 9, 14, 15).
 * Why it is designed this way:
 *   Page 9 & 15 require:
 *   - "Include background and lock-screen controls, audio interruption recovery,
 *      transcripts and clear reconnect states. No automatic playback."
 *   - Configures `expo-av` with `staysActiveInBackground: true` and `playsInSilentModeIOS: true`
 *     so commuters can listen to Mastek Radio with their phone screen locked,
 *     while syncing `positionMillis` back to `/api/v1/listen/progress`!
 * ============================================================================
 */
export class NativeAudioService {
  private sound: Audio.Sound | null = null;

  async configureBackgroundAudio(): Promise<void> {
    await Audio.setAudioModeAsync({
      allowsRecordingIOS: false,
      staysActiveInBackground: true,
      interruptionModeIOS: InterruptionModeIOS.DoNotMix,
      playsInSilentModeIOS: true,
      shouldDuckAndroid: true,
      interruptionModeAndroid: InterruptionModeAndroid.DoNotMix,
      playThroughEarpieceAndroid: false,
    });
  }

  async playFromSignedUrl(
    signedUrl: string,
    resumePositionSeconds: number,
    onProgressSec: (sec: number) => void
  ): Promise<void> {
    await this.configureBackgroundAudio();
    if (this.sound) {
      await this.sound.unloadAsync();
      this.sound = null;
    }

    const { sound } = await Audio.Sound.createAsync(
      { uri: signedUrl },
      { shouldPlay: true, positionMillis: resumePositionSeconds * 1000 },
      (status) => {
        if (status.isLoaded && status.positionMillis != null) {
          onProgressSec(Math.floor(status.positionMillis / 1000));
        }
      }
    );
    this.sound = sound;
  }

  async pause(): Promise<void> {
    if (this.sound) {
      await this.sound.pauseAsync();
    }
  }
}
