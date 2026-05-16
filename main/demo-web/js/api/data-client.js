// Data API client module
import { log } from '../utils/logger.js';

/**
 * Data API client for fetching chat history, memory, and profile data
 */
class DataClient {
    constructor() {
        this.baseUrl = '';
        this.deviceId = '';
        this.clientId = '';
    }

    /**
     * Initialize the client with connection parameters
     * @param {string} otaUrl - OTA server URL (e.g., http://localhost:8002/xiaozhi/ota/)
     * @param {string} deviceId - Device MAC address
     * @param {string} clientId - Client ID
     */
    init(otaUrl, deviceId, clientId) {
        // Extract base URL from OTA URL
        // OTA URL format: http://localhost:8002/xiaozhi/ota/
        // Base URL: http://localhost:8002/xiaozhi/
        const url = new URL(otaUrl);
        this.baseUrl = `${url.protocol}//${url.host}${url.pathname.replace('/ota/', '/')}`;
        this.deviceId = deviceId;
        this.clientId = clientId;

        log(`DataClient initialized: baseUrl=${this.baseUrl}, deviceId=${deviceId}`, 'info');
    }

    /**
     * Check if client is initialized
     * @returns {boolean}
     */
    isInitialized() {
        return this.baseUrl && this.deviceId;
    }

    /**
     * Make an API request
     * @param {string} endpoint - API endpoint
     * @param {Object} params - Query parameters
     * @returns {Promise<Object>}
     */
    async makeRequest(endpoint, params = {}) {
        if (!this.isInitialized()) {
            throw new Error('DataClient not initialized. Please connect to device first.');
        }

        try {
            // Build URL with query parameters
            const url = new URL(endpoint, this.baseUrl);
            Object.keys(params).forEach(key => {
                url.searchParams.append(key, params[key]);
            });

            // Use same authentication as OTA requests
            const headers = {
                'Content-Type': 'application/json',
                'Device-Id': this.deviceId,
                'Client-Id': this.clientId
            };

            log(`Fetching data: ${url.toString()}`, 'info');

            const response = await fetch(url.toString(), {
                method: 'GET',
                headers: headers
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const result = await response.json();

            // Check response code
            if (result.code !== 0) {
                throw new Error(result.msg || 'API request failed');
            }

            return result.data || { list: [], total: 0 };
        } catch (error) {
            log(`API request failed: ${error.message}`, 'error');
            throw error;
        }
    }

    /**
     * Fetch chat history by MAC address
     * @param {string} macAddress - Device MAC address
     * @param {number} page - Page number (default: 1)
     * @param {number} limit - Page size (default: 20)
     * @returns {Promise<Object>}
     */
    async fetchChatHistory(macAddress, page = 1, limit = 20) {
        return this.makeRequest('pet/chat-history/list', {
            macAddress,
            page,
            limit
        });
    }

    /**
     * Fetch memory list by device ID
     * @param {string} deviceId - Device ID (user_id)
     * @param {number} page - Page number (default: 1)
     * @param {number} limit - Page size (default: 20)
     * @returns {Promise<Object>}
     */
    async fetchMemoryList(deviceId, page = 1, limit = 20) {
        return this.makeRequest('pet/memory/list', {
            deviceId,
            page,
            limit
        });
    }

    /**
     * Fetch user profile by device ID
     * @param {string} deviceId - Device ID (user_id)
     * @returns {Promise<Object>}
     */
    async fetchProfile(deviceId) {
        if (!this.isInitialized()) {
            throw new Error('DataClient not initialized. Please connect to device first.');
        }

        try {
            // Build URL with query parameters
            const url = new URL('pet/profile', this.baseUrl);
            url.searchParams.append('deviceId', deviceId);

            // Use same authentication as OTA requests
            const headers = {
                'Content-Type': 'application/json',
                'Device-Id': this.deviceId,
                'Client-Id': this.clientId
            };

            log(`Fetching profile: ${url.toString()}`, 'info');

            const response = await fetch(url.toString(), {
                method: 'GET',
                headers: headers
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const result = await response.json();

            // Check response code
            if (result.code !== 0) {
                throw new Error(result.msg || 'API request failed');
            }

            return result.data || null;
        } catch (error) {
            log(`Profile fetch failed: ${error.message}`, 'error');
            throw error;
        }
    }
}

// Create singleton instance
export const dataClient = new DataClient();

// Export class for module usage
export { DataClient };
