import { toFolderCategoryValue } from "../../constants/folderCategories";
import { toVisibilityApiValue } from "../../constants/visibilityOptions";
import { apiFetch } from "../../lib/apiClient";

const unwrap = (response) => response?.data ?? response;

export const getFolders = async ({ scope = "private", page = 0, size = 100 } = {}) =>
  unwrap(await apiFetch("/folders", { params: { scope, page, size } }));

export const getFolder = async (id) => unwrap(await apiFetch(`/folders/${id}`));

export const createFolder = async (payload) =>
  unwrap(await apiFetch("/folders", {
    method: "POST",
    body: toFolderRequest(payload),
  }));

export const updateFolder = async (id, payload) =>
  unwrap(await apiFetch(`/folders/${id}`, {
    method: "PUT",
    body: toFolderRequest(payload),
  }));

export const deleteFolder = async (id) =>
  unwrap(await apiFetch(`/folders/${id}`, {
    method: "DELETE",
  }));

export const toFolderRequest = (folder) => ({
  title: folder.title?.trim(),
  description: folder.description || "",
  subjectId: folder.subjectId || "",
  semester: folder.semester || "Semester 1",
  category: folder.category?.includes("_") ? folder.category : toFolderCategoryValue(folder.category),
  visibility: folder.visibility?.toUpperCase?.() === folder.visibility
    ? folder.visibility
    : toVisibilityApiValue(folder.visibility),
  groupId: folder.visibility === "group" || folder.visibility === "GROUP" ? folder.groupId : "",
});
