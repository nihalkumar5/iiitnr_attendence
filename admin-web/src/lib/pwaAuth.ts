/**
 * Anti-Proxy Single-Device Hardware Lock Module for iOS PWA & Web
 * Zero biometric popups, zero Face ID, zero Fingerprint friction.
 * Enforces: One Phone = One Student Roll Number.
 */

export interface DeviceAuthResult {
  success: boolean;
  deviceId: string;
  signature: string;
  timestamp: string;
  error?: string;
}

const STORAGE_DEVICE_KEY = "iiitnr_pwa_device_id";
const STORAGE_BOUND_ROLL = "iiitnr_pwa_bound_roll";

/**
 * Returns or generates a persistent device key locked to this Safari/Browser instance
 */
export function getOrCreatePwaDeviceId(): string {
  if (typeof window === "undefined") return "DEV-IOS-PWA-MOCK";
  let devId = localStorage.getItem(STORAGE_DEVICE_KEY);
  if (!devId) {
    const randomHex = Array.from(crypto.getRandomValues(new Uint8Array(8)))
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("")
      .toUpperCase();
    devId = `DEV-IOS-${randomHex.substring(0, 8)}`;
    localStorage.setItem(STORAGE_DEVICE_KEY, devId);
  }
  return devId;
}

/**
 * Anti-Proxy check: Ensures browser is permanently locked to this exact student roll number.
 * Multiple student logins from a single phone are blocked to prevent proxy.
 */
export function verifyDeviceBinding(rollNumber: string): { isBound: boolean; boundTo?: string } {
  if (typeof window === "undefined") return { isBound: true };
  const currentBound = localStorage.getItem(STORAGE_BOUND_ROLL);
  if (!currentBound) {
    localStorage.setItem(STORAGE_BOUND_ROLL, rollNumber.trim().toUpperCase());
    return { isBound: true };
  }
  if (currentBound.toUpperCase() === rollNumber.trim().toUpperCase()) {
    return { isBound: true };
  }
  return { isBound: false, boundTo: currentBound };
}

/**
 * Reset local browser binding (e.g. if student legitimately re-registers or unbinds)
 */
export function unbindPwaDevice(): void {
  if (typeof window === "undefined") return;
  localStorage.removeItem(STORAGE_BOUND_ROLL);
}

/**
 * Generates cryptographic device signature for attendance submission
 * (Zero biometric popups, zero Face ID, instant 1-tap)
 */
export async function generateAttendanceToken(rollNumber: string): Promise<DeviceAuthResult> {
  const deviceId = getOrCreatePwaDeviceId();
  const timestamp = new Date().toISOString();

  // Strict anti-proxy check: Ensure phone isn't being used by another student
  const binding = verifyDeviceBinding(rollNumber);
  if (!binding.isBound) {
    return {
      success: false,
      deviceId,
      signature: "",
      timestamp,
      error: `ANTI_PROXY_VIOLATION: This phone is permanently locked to Roll ${binding.boundTo}. Multiple student logins from a single device are blocked.`,
    };
  }

  try {
    let signature: string;

    // crypto.subtle only works in secure context (HTTPS). Fall back to simple hash for HTTP local dev.
    if (
      typeof window !== "undefined" &&
      window.crypto?.subtle &&
      window.isSecureContext
    ) {
      const rawData = new TextEncoder().encode(`${rollNumber}:${deviceId}:${timestamp}`);
      const hashBuffer = await window.crypto.subtle.digest("SHA-256", rawData);
      const hashArray = Array.from(new Uint8Array(hashBuffer));
      signature = hashArray.map((b) => b.toString(16).padStart(2, "0")).join("").substring(0, 32);
    } else {
      // Fallback: simple deterministic hash for HTTP/non-secure contexts (local testing)
      const raw = `${rollNumber}:${deviceId}:${timestamp}`;
      let h = 0x811c9dc5;
      for (let i = 0; i < raw.length; i++) {
        h ^= raw.charCodeAt(i);
        h = (h * 0x01000193) >>> 0;
      }
      const extra = deviceId.replace(/[^a-zA-Z0-9]/g, "").substring(0, 8);
      signature = h.toString(16).padStart(8, "0") + extra + rollNumber.replace(/[^a-zA-Z0-9]/g, "").substring(0, 8);
    }

    return {
      success: true,
      deviceId,
      signature: `SIG-PWA-${signature.substring(0, 32)}`,
      timestamp,
    };
  } catch (err: any) {
    return {
      success: false,
      deviceId,
      signature: "",
      timestamp,
      error: err?.message || "Failed to generate attendance signature.",
    };
  }
}
