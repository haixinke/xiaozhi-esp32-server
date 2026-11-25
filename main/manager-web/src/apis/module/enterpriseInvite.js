import { getServiceUrl } from '../api';
import RequestService from '../httpRequest';

function request(path, method = 'GET', data, responseType) {
  return new Promise((resolve, reject) => {
    const builder = RequestService.sendRequest()
      .url(`${getServiceUrl()}${path}`)
      .method(method)
      .success(({ data: response }) => resolve(response))
      .fail(reject)
      .networkFail(reject);
    if (data !== undefined) builder.data(data);
    if (responseType) builder.type(responseType);
    builder.send();
  });
}

export default {
  async list({ page, limit, status }) {
    const query = new URLSearchParams({ type: '2', page, limit });
    if (status === 0 || status === 1) query.set('status', status);
    const result = await request(`/invite/page?${query}`);
    return result.data;
  },

  create(data) {
    return request('/invite', 'POST', data);
  },

  update(data) {
    return request('/invite', 'PUT', data);
  },

  async usage(id, { page, limit }) {
    const query = new URLSearchParams({ page, limit });
    const result = await request(`/invite/${encodeURIComponent(id)}/usage?${query}`);
    return result.data;
  },

  async downloadQrCode(id) {
    const blob = await request(`/invite/${encodeURIComponent(id)}/qrcode`, 'GET', undefined, 'blob');
    if (!(blob instanceof Blob) || blob.type !== 'image/png' || !blob.size) {
      throw new Error('Invalid PNG response');
    }
    return blob;
  }
};
