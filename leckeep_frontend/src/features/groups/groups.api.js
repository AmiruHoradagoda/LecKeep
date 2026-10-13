import { apiFetch } from "../../lib/apiClient";

const unwrap = (response) => response?.data ?? response;

export const getGroups = async ({ page = 0, size = 100, scope = "owned" } = {}) =>
  unwrap(await apiFetch("/groups", { params: { page, size, scope } }));

export const createGroup = async (payload) =>
  unwrap(await apiFetch("/groups", {
    method: "POST",
    body: payload,
  }));

export const updateGroup = async (id, payload) =>
  unwrap(await apiFetch(`/groups/${id}`, {
    method: "PUT",
    body: payload,
  }));

export const deleteGroup = async (id) =>
  unwrap(await apiFetch(`/groups/${id}`, {
    method: "DELETE",
  }));

export const addGroupMember = async (groupId, payload) =>
  unwrap(await apiFetch(`/groups/${groupId}/members`, {
    method: "POST",
    body: payload,
  }));

export const removeGroupMember = async (groupId, userId) =>
  unwrap(await apiFetch(`/groups/${groupId}/members/${userId}`, {
    method: "DELETE",
  }));
