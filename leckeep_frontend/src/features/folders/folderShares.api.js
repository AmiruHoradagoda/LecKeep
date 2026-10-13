import { apiFetch } from "../../lib/apiClient";

const unwrap = (response) => response?.data ?? response;
const path = (folderId) => `/folders/${encodeURIComponent(folderId)}/share`;

export const getFolderShares = async (folderId) => unwrap(await apiFetch(path(folderId)));

export const createFolderShare = async (folderId, payload) =>
  unwrap(await apiFetch(path(folderId), { method: "POST", body: payload }));

export const revokeFolderShare = async (folderId, shareId) =>
  unwrap(await apiFetch(path(folderId), { method: "DELETE", params: { shareId } }));
