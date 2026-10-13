import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowLeft,
  BookOpen,
  Calendar,
  Edit,
  Eye,
  FileText,
  Folder,
  Grid3X3,
  Globe2,
  ImageIcon,
  LockKeyhole,
  Plus,
  RefreshCw,
  Share2,
  Trash2,
  Upload,
  UserPlus,
  Users,
  X,
} from "lucide-react";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Input } from "../../components/ui/Input";
import { categories, toFolderCategoryLabel } from "../../constants/folderCategories";
import { semesters } from "../../constants/semesters";
import { toVisibilityUiValue, visibilityOptions } from "../../constants/visibilityOptions";
import { getUserId } from "../../lib/apiClient";
import { getUsers } from "../users/users.api";
import DashboardView from "../dashboard/DashboardView";
import { addGroupMember, createGroup, getGroups } from "../groups/groups.api";
import GroupsView from "../groups/GroupsView";
import { createSubject, deleteSubject, getSubjects } from "../subjects/subjects.api";
import SubjectsView from "../subjects/SubjectsView";
import { createFolder, deleteFolder, getFolder, getFolders } from "../folders/folders.api";
import ShareFolderDialog from "../folders/ShareFolderDialog";
import FoldersView from "../folders/FoldersView";
import DocumentPreviewDialog from "../documents/DocumentPreviewDialog";
import { getDocumentDownloadUrl, getFolderDocuments, previewDocument, uploadDocument } from "../documents/documents.api";
import AddNoteDialog from "../notes/AddNoteDialog";
import NotesView from "../notes/NotesView";
import { allowedDocumentExtensions, documentAccept } from "../notes/notes.constants";
import {
  defaultGroups,
  defaultSubjects,
  demoSubjectCounts,
  dummyNoteMeta,
  dummyNotes,
  dummyUsers,
} from "./workspaceDemoData";

const noteScopes = [
  { id: "global", label: "Global", description: "Notes published for everyone", Icon: Globe2 },
  { id: "private", label: "Private", description: "Only visible to you", Icon: LockKeyhole },
  { id: "shared", label: "Published", description: "Folders you published through visibility", Icon: Share2 },
  { id: "group", label: "Group", description: "Notes shared inside groups", Icon: Users },
];
const getDocumentName = (document) =>
  typeof document === "string"
    ? document
    : document?.originalName || document?.name || "Document";

const sanitizeNote = (note) => ({
  id: note?.id || `temp-${Date.now()}-${Math.random()}`,
  title: note?.title || "Untitled lecture note",
  content: note?.content || "",
  createdAt: note?.createdAt || new Date().toISOString(),
  userId: note?.userId || "",
  tags: Array.isArray(note?.tags) ? note.tags : [],
  attachmentName: note?.attachmentName || note?.pdfName || "",
});

const hiddenPlaceholderTitles = new Set(["njnj"]);
const isHiddenPlaceholderNote = (note) =>
  hiddenPlaceholderTitles.has((note?.title || "").trim().toLowerCase());

const sanitizeNotes = (noteList) =>
  Array.isArray(noteList)
    ? noteList.map(sanitizeNote).filter((note) => !isHiddenPlaceholderNote(note))
    : [];

const currentAuthorName = () => {
  const userId = getUserId();
  return userId ? `You (${userId.slice(-4)})` : "You";
};

const folderToNote = (folder, subjectRecords = [], documents = []) => {
  const subject = subjectRecords.find((item) => item.id === folder.subjectId);
  const documentNames = documents.map(getDocumentName);
  const firstPdf = documents.find((document) => getFileExtension(document) === ".pdf");

  return sanitizeNote({
    id: folder.id,
    title: folder.title,
    content: folder.description || "",
    createdAt: folder.createdAt,
    userId: folder.ownerId,
    tags: [subject?.name || "General"],
    attachmentName: firstPdf ? getDocumentName(firstPdf) : documentNames[0] || "",
  });
};

const groupToViewModel = (group) => ({
  ...group,
  admin: group.ownerId ? `Owner (${group.ownerId.slice(-4)})` : "Owner",
  members: group.members || ["Owner"],
});

const normalizeSubject = (subject) => {
  if (typeof subject === "string") {
    return { name: subject.trim(), semester: "Semester 1" };
  }

  return {
    id: subject?.id,
    name: subject?.name?.trim() || "",
    semester: semesters.includes(subject?.semester) ? subject.semester : "Semester 1",
  };
};

const uniqueSubjects = (items) => {
  const byName = new Map();
  items.map(normalizeSubject).forEach((subject) => {
    if (subject.name && !byName.has(subject.name)) byName.set(subject.name, subject);
  });
  return [...byName.values()].sort((a, b) =>
    a.semester === b.semester
      ? a.name.localeCompare(b.name)
      : semesters.indexOf(a.semester) - semesters.indexOf(b.semester)
  );
};

const uniqueValues = (items) =>
  [...new Set(items.map((item) => item.trim()).filter(Boolean))].sort((a, b) =>
    a.localeCompare(b)
  );

const formatDate = (value) => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "Recently";
  return date.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
};

const displayFileName = (document) => getDocumentName(document).replace(/\.[^/.]+$/, "");

const getFileExtension = (document) => {
  const match = getDocumentName(document).toLowerCase().match(/\.[^/.]+$/);
  return match ? match[0] : "";
};

const isAllowedDocument = (fileName) => allowedDocumentExtensions.includes(getFileExtension(fileName));

const getDocumentIcon = (fileName, size = 38) => {
  const extension = getFileExtension(fileName);
  if ([".png", ".jpg", ".jpeg"].includes(extension)) {
    return <ImageIcon className="text-primary" size={size} />;
  }

  const colorClass = {
    ".pdf": "text-red-600",
    ".doc": "text-blue-600",
    ".docx": "text-blue-600",
    ".ppt": "text-orange-600",
    ".pptx": "text-orange-600",
    ".xls": "text-green-600",
    ".xlsx": "text-green-600",
  }[extension] || "text-muted-foreground";

  return <FileText className={colorClass} size={size} />;
};

const MainContent = ({ activeSection = "subjects", searchResults, searchQuery = "", searchLoading, searchError, onNavigate }) => {
  const [resultOpening, setResultOpening] = useState(false);
  const [resultOpenError, setResultOpenError] = useState("");
  const resultRequest = useRef(0);

  useEffect(() => {
    resultRequest.current += 1;
    setResultOpening(false);
    setResultOpenError("");
    return () => { resultRequest.current += 1; };
  }, [searchQuery, activeSection]);
  const [notes, setNotes] = useState([]);
  const [subjects, setSubjects] = useState([]);
  const [groups, setGroups] = useState([]);
  const [users, setUsers] = useState([]);
  const [noteMeta, setNoteMeta] = useState({});
  const [filters, setFilters] = useState({ semester: "all", subject: "all", category: "all" });
  const [subjectSemesterFilter, setSubjectSemesterFilter] = useState("all");
  const [activeNoteScope, setActiveNoteScope] = useState("private");
  const [selectedNote, setSelectedNote] = useState(null);
  const [previewDocumentFile, setPreviewDocumentFile] = useState(null);
  const [documentPreview, setDocumentPreview] = useState(null);
  const [documentPreviewLoading, setDocumentPreviewLoading] = useState(false);
  const [documentPreviewError, setDocumentPreviewError] = useState("");
  const [shareNote, setShareNote] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [newSubjectName, setNewSubjectName] = useState("");
  const [newSubjectSemester, setNewSubjectSemester] = useState("Semester 1");
  const [showSubjectForm, setShowSubjectForm] = useState(false);
  const [showGroupForm, setShowGroupForm] = useState(false);
  const [newGroupName, setNewGroupName] = useState("");
  const [groupRegistrationSearch, setGroupRegistrationSearch] = useState("");
  const [selectedGroupUsers, setSelectedGroupUsers] = useState([]);
  const [selectedDocuments, setSelectedDocuments] = useState([]);
  const [newNote, setNewNote] = useState({
    title: "",
    subject: "",
    semester: "",
    category: "",
    visibility: "private",
    groupId: "",
  });

  const fetchWorkspace = useCallback(async () => {
    setLoading(true);
    try {
      const [subjectList, groupPage, userList] = await Promise.all([
        getSubjects(),
        getGroups({ size: 100, scope: "shareable" }),
        getUsers().catch(() => []),
      ]);

      const cleanSubjects = uniqueSubjects(subjectList);
      const allGroups = (groupPage?.dataList || []).map(groupToViewModel);
      const folderPages = await Promise.all(
        ["private", "global", "group"].map((scope) => getFolders({ scope, size: 100 }))
      );
      const folders = folderPages.flatMap((page) => page?.dataList || []);
      const folderDocuments = await Promise.all(
        folders.map(async (folder) => {
          try {
            const documentsPage = await getFolderDocuments(folder.id, { size: 100 });
            return [folder.id, documentsPage?.dataList || []];
          } catch {
            return [folder.id, []];
          }
        })
      );
      const documentsByFolderId = Object.fromEntries(folderDocuments);
      const backendNotes = folders.map((folder) =>
        folderToNote(folder, cleanSubjects, documentsByFolderId[folder.id] || [])
      );
      const backendMeta = Object.fromEntries(folders.map((folder) => {
        const subject = cleanSubjects.find((item) => item.id === folder.subjectId);
        const documents = documentsByFolderId[folder.id] || [];
        return [
          folder.id,
          {
            subject: subject?.name || "General",
            subjectId: folder.subjectId,
            semester: folder.semester || "Semester 1",
            category: toFolderCategoryLabel(folder.category),
            categoryValue: folder.category,
            attachmentName: documents.map(getDocumentName).find((fileName) => getFileExtension(fileName) === ".pdf") || "",
            documents,
            visibility: toVisibilityUiValue(folder.visibility),
            groupId: folder.groupId || "",
            author: folder.ownerId === getUserId() ? currentAuthorName() : `User (${folder.ownerId?.slice(-4) || "unknown"})`,
            sharedByMe: folder.ownerId === getUserId() && folder.visibility !== "PRIVATE",
            sharedTo: folder.visibility === "GROUP" ? "group" : folder.visibility === "GLOBAL" ? "global" : "",
          },
        ];
      }));

      setSubjects(cleanSubjects);
      setGroups(allGroups);
      setUsers(userList.length > 0 ? userList : dummyUsers);
      setNotes(backendNotes);
      setNoteMeta(backendMeta);
      setError("");
    } catch (err) {
      console.error("Fetch error:", err);
      setSubjects(uniqueSubjects(defaultSubjects));
      setGroups(defaultGroups);
      setUsers(dummyUsers);
      setNotes(dummyNotes.map(sanitizeNote));
      setNoteMeta(dummyNoteMeta);
      setError("");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchWorkspace();
  }, [fetchWorkspace]);

  const noteSubjectNames = useMemo(
    () => uniqueValues(notes.flatMap((note) => note.tags || [])),
    [notes]
  );

  const allSubjectRecords = useMemo(
    () => uniqueSubjects([...subjects, ...noteSubjectNames.map((name) => ({ name, semester: "Semester 1" }))]),
    [subjects, noteSubjectNames]
  );

  const allSubjects = useMemo(
    () => allSubjectRecords.map((subject) => subject.name),
    [allSubjectRecords]
  );

  const saveNoteMeta = (nextMeta) => {
    setNoteMeta(nextMeta);
  };

  const subjectSemester = (subjectName) =>
    allSubjectRecords.find((subject) => subject.name === subjectName)?.semester || "Semester 1";

  const getNoteView = (note) => {
    const subject = note.tags?.[0] || "General";
    const meta = noteMeta[note.id] || {};
    const legacyDocuments = [
      meta.attachmentName || note.attachmentName,
      ...(Array.isArray(meta.images) ? meta.images : []),
    ].filter(Boolean);
    const documents = Array.isArray(meta.documents) ? meta.documents : legacyDocuments;
    return {
      ...note,
      subject: meta.subject || subject,
      subjectId: meta.subjectId || "",
      semester: meta.semester || subjectSemester(subject),
      category: meta.category || "Lecture",
      categoryValue: meta.categoryValue || "",
      attachmentName: meta.attachmentName || note.attachmentName || documents.find((fileName) => getFileExtension(fileName) === ".pdf") || "",
      documents,
      visibility: meta.visibility || "private",
      author: meta.author || currentAuthorName(),
      groupId: meta.groupId || "",
      sharedByMe: Boolean(meta.sharedByMe),
      sharedTo: meta.sharedTo || "",
    };
  };

  const noteViews = useMemo(
    () => notes.map(getNoteView),
    [notes, noteMeta, allSubjectRecords]
  );

  const filteredNotes = useMemo(() => {
    return noteViews.filter((note) => {
      const matchesSemester = filters.semester === "all" || note.semester === filters.semester;
      const matchesSubject = filters.subject === "all" || note.subject === filters.subject;
      const matchesCategory = filters.category === "all" || note.category === filters.category;
      return matchesSemester && matchesSubject && matchesCategory;
    });
  }, [noteViews, filters]);

  const scopeNotes = useMemo(
    () =>
      filteredNotes.filter((note) => {
        if (activeNoteScope === "shared") return note.sharedByMe;
        return note.visibility === activeNoteScope;
      }),
    [filteredNotes, activeNoteScope]
  );

  const noteScopeCounts = useMemo(
    () =>
      noteScopes.reduce((counts, scope) => {
        counts[scope.id] = filteredNotes.filter((note) => {
          if (scope.id === "shared") return note.sharedByMe;
          return note.visibility === scope.id;
        }).length;
        return counts;
      }, {}),
    [filteredNotes]
  );

  const groupName = (groupId) =>
    groups.find((group) => group.id === groupId)?.name || "No group selected";

  const getFolderResources = (note) => {
    const documents = Array.isArray(note.documents) ? note.documents : [note.attachmentName].filter(Boolean);
    return {
      documents,
      total: documents.length,
    };
  };

  const subjectRows = useMemo(
    () =>
      allSubjectRecords.map((subject) => {
        const realNotes = noteViews.filter((note) => note.subject === subject.name);
        const demo = demoSubjectCounts[subject.name] || { notes: 0, documents: 0 };
        return {
          ...subject,
          notes: realNotes.length || demo.notes,
          documents: realNotes.reduce((count, note) => count + getFolderResources(note).total, 0) || demo.documents,
          canRemove: subjects.some((item) => item.name === subject.name),
        };
      }),
    [allSubjectRecords, noteViews, subjects]
  );

  const visibleSubjectRows = useMemo(() => {
    if (subjectSemesterFilter === "all") return subjectRows;
    return subjectRows.filter((subject) => subject.semester === subjectSemesterFilter);
  }, [subjectRows, subjectSemesterFilter]);

  const handleAddSubject = async (event) => {
    event.preventDefault();
    if (!newSubjectName.trim()) return;

    try {
      const subject = await createSubject({
        name: newSubjectName.trim(),
        semester: newSubjectSemester,
      });
      setSubjects((prev) => uniqueSubjects([...prev, subject]));
      setNewSubjectName("");
      setNewSubjectSemester("Semester 1");
      setShowSubjectForm(false);
      setError("");
    } catch (err) {
      setError(err.message || "Failed to create subject");
    }
  };

  const handleRemoveSubject = async (subjectName) => {
    const subject = subjects.find((item) => item.name === subjectName);
    if (!subject?.id) return;

    try {
      await deleteSubject(subject.id);
      setSubjects((prev) => prev.filter((item) => item.id !== subject.id));
      if (filters.subject === subjectName) setFilters((prev) => ({ ...prev, subject: "all" }));
      setError("");
    } catch (err) {
      setError(err.message || "Failed to delete subject");
    }
  };

  const groupUserOptions = useMemo(() => {
    const query = groupRegistrationSearch.trim().toLowerCase();
    if (!query) return [];

    return users
      .filter((user) => !selectedGroupUsers.some((selectedUser) => selectedUser.id === user.id))
      .filter((user) =>
        (user.registrationNumber || user.registrationNo || "").toLowerCase().includes(query) ||
        (user.username || user.name || "").toLowerCase().includes(query)
      )
      .slice(0, 5);
  }, [groupRegistrationSearch, selectedGroupUsers, users]);

  const resetGroupForm = () => {
    setNewGroupName("");
    setGroupRegistrationSearch("");
    setSelectedGroupUsers([]);
    setShowGroupForm(false);
  };

  const addGroupUser = (user) => {
    setSelectedGroupUsers((prev) =>
      prev.some((selectedUser) => selectedUser.id === user.id) ? prev : [...prev, user]
    );
    setGroupRegistrationSearch("");
  };

  const removeGroupUser = (userId) => {
    setSelectedGroupUsers((prev) => prev.filter((user) => user.id !== userId));
  };

  const userInitials = (name) =>
    (name || "")
      .split(" ")
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0]?.toUpperCase())
      .join("");

  const handleDocumentChange = (event) => {
    const files = Array.from(event.target.files || []);
    if (files.length === 0) return;

    const invalidFiles = files.filter((file) => !isAllowedDocument(file.name));
    if (invalidFiles.length > 0) {
      setError("Please select only PDF, image, Word, PowerPoint, or Excel files.");
      return;
    }

    setSelectedDocuments((prev) => {
      const byName = new Map(prev.map((file) => [file.name, file]));
      files.forEach((file) => byName.set(file.name, file));
      return [...byName.values()];
    });
    setError("");
  };

  const handleCreateNote = async (event) => {
    event.preventDefault();
    try {
      const userId = getUserId();
      if (!userId) {
        setError("User ID not found. Please log in again.");
        return;
      }

      if (selectedDocuments.length === 0) {
        setError("Please upload at least one document.");
        return;
      }

      if (newNote.visibility === "group" && !newNote.groupId) {
        setError("Please select a group for this note folder.");
        return;
      }

      const subjectName = newNote.subject.trim();
      let subject = subjects.find((item) => item.name === subjectName);
      if (!subject?.id) {
        subject = await createSubject({
          name: subjectName,
          semester: newNote.semester || "Semester 1",
        });
        setSubjects((prev) => uniqueSubjects([...prev, subject]));
      }

      const folder = await createFolder({
        title: newNote.title,
        description: "",
        subjectId: subject.id,
        semester: newNote.semester || subject.semester || "Semester 1",
        category: newNote.category || "Lecture",
        visibility: newNote.visibility || "private",
        groupId: newNote.visibility === "group" ? newNote.groupId : "",
      });

      const uploadedDocuments = await Promise.all(
        selectedDocuments.map((file) => uploadDocument(folder.id, file))
      );
      const cleanNote = folderToNote(folder, [...subjects, subject], uploadedDocuments);
      setNotes((prev) => [cleanNote, ...prev]);
      saveNoteMeta({
        ...noteMeta,
        [cleanNote.id]: {
          subject: subject.name,
          subjectId: subject.id,
          semester: folder.semester || newNote.semester || "Semester 1",
          category: newNote.category || "Lecture",
          categoryValue: folder.category,
          attachmentName: uploadedDocuments.map(getDocumentName).find((fileName) => getFileExtension(fileName) === ".pdf") || "",
          documents: uploadedDocuments,
          visibility: newNote.visibility || "private",
          groupId: newNote.visibility === "group" ? newNote.groupId : "",
          author: currentAuthorName(),
          sharedByMe: false,
          sharedTo: "",
        },
      });
      setNewNote({ title: "", subject: "", semester: "", category: "", visibility: "private", groupId: "" });
      setSelectedDocuments([]);
      setActiveNoteScope(newNote.visibility || "private");
      setError("");
    } catch (err) {
      setError(err.message || "Failed to create note");
      console.error("Create error:", err);
    }
  };

  const handleNoteUpdate = (updatedNote) => {
    const cleanNote = sanitizeNote(updatedNote);
    setNotes((prev) => prev.map((note) => (note.id === cleanNote.id ? cleanNote : note)));
  };

  const handleNoteDelete = async (noteId) => {
    try {
      await deleteFolder(noteId);
      setNotes((prev) => prev.filter((note) => note.id !== noteId));
      if (selectedNote?.id === noteId) setSelectedNote(null);
      const nextMeta = { ...noteMeta };
      delete nextMeta[noteId];
      saveNoteMeta(nextMeta);
      setError("");
    } catch (err) {
      setError(err.message || "Failed to delete folder");
    }
  };

  const closeDocumentPreview = () => {
    setPreviewDocumentFile(null);
    setDocumentPreview(null);
    setDocumentPreviewError("");
    setDocumentPreviewLoading(false);
  };

  const openDocumentPreview = async (document) => {
    setPreviewDocumentFile(document);
    setDocumentPreview(null);
    setDocumentPreviewError("");

    if (!document?.id) {
      setDocumentPreviewError("Preview is available only for uploaded backend documents.");
      return;
    }

    setDocumentPreviewLoading(true);
    try {
      const preview = await previewDocument(document.id);
      setDocumentPreview(preview);
    } catch (err) {
      setDocumentPreviewError(err.message || "Failed to load document preview");
    } finally {
      setDocumentPreviewLoading(false);
    }
  };

  const openSearchResult = async (result) => {
    if (result.type === "SUBJECT") {
      setSelectedNote(null);
      setActiveNoteScope("private");
      setFilters({ semester: "all", subject: result.name, category: "all" });
      onNavigate("notes");
      return;
    }
    const requestId = ++resultRequest.current;
    setResultOpening(true);
    setResultOpenError("");
    try {
      const folderId = result.type === "DOCUMENT" ? result.folderId : result.id;
      const folder = await getFolder(folderId);
      const page = await getFolderDocuments(folderId, { size: 100 });
      if (requestId !== resultRequest.current) return;
      const documents = page?.dataList || [];
      const subject = subjects.find((item) => item.id === folder.subjectId);
      setSelectedNote({
        ...folderToNote(folder, subjects, documents),
        subject: subject?.name || "General",
        subjectId: folder.subjectId,
        semester: folder.semester,
        category: toFolderCategoryLabel(folder.category),
        categoryValue: folder.category,
        visibility: toVisibilityUiValue(folder.visibility),
        groupId: folder.groupId,
        documents,
      });
      onNavigate("notes");
      if (result.type === "DOCUMENT") {
        // The matching document may be outside the folder's first page.
        openDocumentPreview(documents.find((item) => item.id === result.id) || {
          id: result.id, originalName: result.name, contentType: result.contentType, extension: result.extension,
        });
      }
    } catch (err) {
      if (requestId === resultRequest.current) setResultOpenError(err.message || "Could not open resource.");
    } finally {
      if (requestId === resultRequest.current) setResultOpening(false);
    }
  };

  const renderSearch = () => (
    <section aria-label="Search results" className="space-y-5">
      <PageHeader title="Search" description={`Results for “${searchQuery.trim()}”`} />
      {searchLoading ? <p role="status">Searching…</p> : searchError ? (
        <p role="alert" className="text-destructive">{searchError}</p>
      ) : searchResults?.length ? (
        <>
          <p className="text-sm text-muted-foreground">{searchResults.length} results · Up to 20 per resource type</p>
          {resultOpenError && <p role="alert" className="text-destructive">{resultOpenError}</p>}
          {resultOpening && <p role="status">Opening resource…</p>}
          {["SUBJECT", "FOLDER", "DOCUMENT"].map((type) => {
            const results = searchResults.filter((result) => result.type === type);
            if (!results.length) return null;
            return (
              <div key={type} className="space-y-3">
                <h2 className="text-lg font-bold">{{ SUBJECT: "Subjects", FOLDER: "Folders", DOCUMENT: "Documents" }[type]}</h2>
                {results.map((result) => (
                  <button type="button" key={`${type}-${result.id}`} disabled={resultOpening}
                    onClick={() => openSearchResult(result)}
                    className="flex w-full items-center gap-4 rounded-xl border border-border bg-white p-4 text-left hover:bg-muted disabled:opacity-60">
                    {type === "SUBJECT" ? <BookOpen size={22} /> : type === "FOLDER" ? <Folder size={22} /> : <FileText size={22} />}
                    <span className="min-w-0 flex-1 break-words font-semibold">{result.name}</span>
                    <Badge variant="outline">{type}</Badge>
                  </button>
                ))}
              </div>
            );
          })}
        </>
      ) : <p role="status">No results found.</p>}
    </section>
  );

  const handleCreateGroup = async (event) => {
    event.preventDefault();
    if (!newGroupName.trim()) return;

    try {
      const group = await createGroup({ name: newGroupName.trim() });
      await Promise.all(
        selectedGroupUsers.map((user) =>
          addGroupMember(group.id, { userId: user.id, role: "MEMBER" })
        )
      );
      const admin = currentAuthorName();
      const nextGroup = groupToViewModel({
        ...group,
        members: uniqueValues([
          admin,
          ...selectedGroupUsers.map((user) =>
            `${user.username || user.name} (${user.registrationNumber || user.registrationNo})`
          ),
        ]),
      });
      setGroups((prev) => [nextGroup, ...prev]);
      resetGroupForm();
      setError("");
    } catch (err) {
      setError(err.message || "Failed to create group");
    }
  };

  const openShareDialog = (note) => setShareNote(note);

  const PageHeader = ({ title, description, action }) => (
    <div className="mb-9 flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight">{title}</h1>
        <p className="mt-2 text-base text-muted-foreground">{description}</p>
      </div>
      {action}
    </div>
  );

  const renderAddNote = () => (
    <div className="max-w-5xl">
      <div className="mb-6">
        <h1 className="text-3xl font-extrabold tracking-tight">Add New Note Folder</h1>
        <p className="mt-2 text-sm text-muted-foreground">Create a Computer Engineering folder and upload lab reports, circuit diagrams, captures, or revision files.</p>
      </div>

      <div className="grid gap-6 xl:grid-cols-[1fr_320px]">
        <form onSubmit={handleCreateNote} className="space-y-5">
          <Card className="bg-card p-6 shadow-none">
            <label className="block space-y-3">
              <span className="text-sm font-bold">Folder Name *</span>
              <Input
                value={newNote.title}
                onChange={(event) => setNewNote((prev) => ({ ...prev, title: event.target.value }))}
                placeholder="e.g., FPGA UART Controller Lab"
                required
                className="bg-white"
              />
            </label>
          </Card>

          <div className="grid gap-5 md:grid-cols-2">
            <Card className="bg-card p-6 shadow-none">
              <label className="block space-y-3">
                <span className="text-sm font-bold">Subject *</span>
                <select
                  value={newNote.subject}
                  onChange={(event) => {
                    const subject = event.target.value;
                    setNewNote((prev) => ({ ...prev, subject, semester: subjectSemester(subject) }));
                  }}
                  required
                  className="h-10 w-full rounded-lg border border-input bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
                >
                  <option value="">Select a subject</option>
                  {allSubjects.map((subject) => (
                    <option key={subject} value={subject}>{subject}</option>
                  ))}
                </select>
              </label>
            </Card>

            <Card className="bg-card p-6 shadow-none">
              <label className="block space-y-3">
                <span className="text-sm font-bold">Semester *</span>
                <select
                  value={newNote.semester}
                  onChange={(event) => setNewNote((prev) => ({ ...prev, semester: event.target.value }))}
                  required
                  className="h-10 w-full rounded-lg border border-input bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
                >
                  <option value="" disabled>Select a semester</option>
                  {semesters.map((semester) => <option key={semester}>{semester}</option>)}
                </select>
              </label>
            </Card>
          </div>

          <Card className="bg-card p-6 shadow-none">
            <label className="block space-y-3">
              <span className="text-sm font-bold">Category *</span>
              <select
                value={newNote.category}
                onChange={(event) => setNewNote((prev) => ({ ...prev, category: event.target.value }))}
                required
                className="h-10 w-full rounded-lg border border-input bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
              >
                <option value="" disabled>Select a category</option>
                {categories.map((category) => <option key={category}>{category}</option>)}
              </select>
            </label>
          </Card>

          <Card className="bg-card p-6 shadow-none">
            <div className="space-y-4">
              <div>
                <h2 className="text-sm font-bold">Folder Visibility *</h2>
                <p className="mt-1 text-xs text-muted-foreground">Choose who can see this note folder.</p>
              </div>
              <div className="grid gap-3 md:grid-cols-3">
                {visibilityOptions.map((option) => {
                  const selected = newNote.visibility === option.value;
                  return (
                    <label
                      key={option.value}
                      className={`cursor-pointer rounded-xl border p-4 transition ${
                        selected ? "border-primary bg-secondary" : "border-border bg-white hover:bg-muted"
                      }`}
                    >
                      <input
                        type="radio"
                        name="visibility"
                        value={option.value}
                        checked={selected}
                        onChange={(event) =>
                          setNewNote((prev) => ({
                            ...prev,
                            visibility: event.target.value,
                            groupId: event.target.value === "group" ? prev.groupId : "",
                          }))
                        }
                        className="sr-only"
                      />
                      <span className="text-sm font-extrabold">{option.label}</span>
                      <p className="mt-2 text-xs leading-5 text-muted-foreground">{option.description}</p>
                    </label>
                  );
                })}
              </div>

              {newNote.visibility === "group" && (
                <label className="block space-y-3">
                  <span className="text-sm font-bold">Select Group *</span>
                  <select
                    value={newNote.groupId}
                    onChange={(event) => setNewNote((prev) => ({ ...prev, groupId: event.target.value }))}
                    required
                    className="h-10 w-full rounded-lg border border-input bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
                  >
                    <option value="">Choose a group</option>
                    {groups.map((group) => <option key={group.id} value={group.id}>{group.name}</option>)}
                  </select>
                </label>
              )}
            </div>
          </Card>

          <Card className="bg-card p-6 shadow-none">
            <div className="space-y-4">
              <span className="text-sm font-bold">Upload Documents *</span>
              <label className="flex min-h-40 cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed border-border bg-white text-center hover:bg-muted">
                <Upload className="mb-3 text-muted-foreground" size={26} />
                <span className="text-sm font-semibold">Click to upload or drag and drop</span>
                <span className="mt-1 text-xs text-muted-foreground">PDF, images, Word, PowerPoint, Excel</span>
                <input type="file" accept={documentAccept} multiple className="hidden" onChange={handleDocumentChange} />
              </label>

              {selectedDocuments.length > 0 && (
                <div className="space-y-2 rounded-lg border border-border bg-white p-3">
                  {selectedDocuments.map((file) => (
                    <div key={getDocumentName(file)} className="flex items-center justify-between gap-3 rounded-lg bg-muted p-3">
                      <div className="flex min-w-0 items-center gap-3">
                        {getDocumentIcon(file, 20)}
                        <p className="truncate text-sm font-bold">{displayFileName(file)}</p>
                      </div>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        onClick={() => setSelectedDocuments((prev) => prev.filter((item) => getDocumentName(item) !== getDocumentName(file)))}
                      >
                        <X size={15} />
                      </Button>
                    </div>
                  ))}
                  <Button type="button" variant="outline" onClick={() => setSelectedDocuments([])}>
                    Clear Documents
                  </Button>
                </div>
              )}
            </div>
          </Card>

          <div className="grid gap-3 sm:grid-cols-2">
            <Button type="submit">Save Folder</Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setNewNote({ title: "", subject: "", semester: "", category: "", visibility: "private", groupId: "" });
                setSelectedDocuments([]);
              }}
            >
              Cancel
            </Button>
          </div>
        </form>

        <aside className="space-y-4">
          <Card className="bg-white p-5 shadow-soft">
            <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-secondary text-primary">
              <Folder size={38} fill="currentColor" strokeWidth={1.5} />
            </div>
            <h2 className="text-lg font-extrabold">Folder Preview</h2>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">
              This form creates one folder in My Notes. Inside the folder you will see the uploaded documents as file cards.
            </p>
            <div className="mt-5 space-y-3 text-sm">
              <div className="flex items-center justify-between rounded-lg bg-muted p-3">
                <span className="font-semibold">Visibility</span>
                <Badge variant="outline">
                  {visibilityOptions.find((option) => option.value === newNote.visibility)?.label || "Private"}
                </Badge>
              </div>
              {newNote.visibility === "group" && (
                <div className="flex items-center justify-between gap-3 rounded-lg bg-muted p-3">
                  <span className="font-semibold">Group</span>
                  <span className="truncate text-muted-foreground">{groupName(newNote.groupId)}</span>
                </div>
              )}
              {selectedDocuments.length > 0 ? (
                selectedDocuments.map((file) => (
                  <div key={getDocumentName(file)} className="flex items-center gap-3 rounded-lg bg-muted p-3">
                    {getDocumentIcon(file, 18)}
                    <span className="truncate font-semibold">{displayFileName(file)}</span>
                  </div>
                ))
              ) : (
                <div className="flex items-center gap-3 rounded-lg bg-muted p-3">
                  <FileText className="text-muted-foreground" size={18} />
                  <span className="font-semibold">Uploaded documents will appear here</span>
                </div>
              )}
            </div>
          </Card>
        </aside>
      </div>
    </div>
  );

  const renderSubjects = () => (
    <div>
      <div className="mb-9 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight">Subjects</h1>
          <p className="mt-2 text-base text-muted-foreground">Manage subjects in a table and assign each to a semester</p>
        </div>
        <Button type="button" onClick={() => setShowSubjectForm((prev) => !prev)}>
          + New Subject
        </Button>
      </div>

      {showSubjectForm && (
        <Card className="mb-6 bg-card p-6 shadow-none">
          <h2 className="mb-5 text-base font-extrabold">Create New Subject</h2>
          <form onSubmit={handleAddSubject} className="grid gap-4 md:grid-cols-[1fr_220px_auto_auto] md:items-end">
            <label className="block space-y-2">
              <span className="text-xs font-bold">Subject Name</span>
              <Input
                value={newSubjectName}
                onChange={(event) => setNewSubjectName(event.target.value)}
                placeholder="e.g., VLSI Design"
                className="bg-white"
              />
            </label>
            <label className="block space-y-2">
              <span className="text-xs font-bold">Semester</span>
              <select
                value={newSubjectSemester}
                onChange={(event) => setNewSubjectSemester(event.target.value)}
                className="h-10 w-full rounded-lg border border-input bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
              >
                {semesters.map((semester) => <option key={semester}>{semester}</option>)}
              </select>
            </label>
            <Button type="submit">Create</Button>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setNewSubjectName("");
                setNewSubjectSemester("Semester 1");
                setShowSubjectForm(false);
              }}
            >
              Cancel
            </Button>
          </form>
        </Card>
      )}

      <Card className="mb-4 bg-card p-4 shadow-none">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-sm font-bold">Filter subjects</p>
            <p className="mt-1 text-xs text-muted-foreground">Show subjects by selected semester.</p>
          </div>
          <select
            value={subjectSemesterFilter}
            onChange={(event) => setSubjectSemesterFilter(event.target.value)}
            className="h-10 min-w-52 rounded-lg border border-input bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
          >
            <option value="all">All semesters</option>
            {semesters.map((semester) => <option key={semester}>{semester}</option>)}
          </select>
        </div>
      </Card>

      <Card className="overflow-hidden bg-white shadow-soft">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[780px] text-left text-sm">
            <thead className="bg-card text-xs uppercase tracking-wide text-muted-foreground">
              <tr>
                <th className="px-5 py-4">Subject</th>
                <th className="px-5 py-4">Semester</th>
                <th className="px-5 py-4 text-center">Notes</th>
                <th className="px-5 py-4 text-center">Documents</th>
                <th className="px-5 py-4 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {visibleSubjectRows.map((subject) => (
                <tr key={subject.name} className="hover:bg-muted/50">
                  <td className="px-5 py-4">
                    <div className="flex items-center gap-3">
                      <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-secondary text-primary">
                        <BookOpen size={18} />
                      </div>
                      <span className="font-bold">{subject.name}</span>
                    </div>
                  </td>
                  <td className="px-5 py-4 text-muted-foreground">{subject.semester}</td>
                  <td className="px-5 py-4 text-center font-bold">{subject.notes}</td>
                  <td className="px-5 py-4 text-center font-bold">{subject.documents}</td>
                  <td className="px-5 py-4">
                    <div className="flex justify-end gap-2">
                      <Button type="button" variant="ghost" size="icon" title="Edit subject">
                        <Edit size={16} />
                      </Button>
                      {subject.canRemove && (
                        <Button type="button" variant="ghost" size="icon" className="text-destructive hover:bg-red-50" onClick={() => handleRemoveSubject(subject.name)} title="Delete subject">
                          <Trash2 size={16} />
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
              {visibleSubjectRows.length === 0 && (
                <tr>
                  <td className="px-5 py-8 text-center text-muted-foreground" colSpan="5">
                    No subjects found for this semester.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );

  const renderFolderCard = (note) => {
    const scope = noteScopes.find((item) => item.id === note.visibility);
    return (
      <Card key={note.id} className="bg-white p-5 shadow-soft transition hover:-translate-y-0.5">
        <div className="mb-4 flex items-start justify-between gap-3">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-secondary text-primary">
            <Folder size={38} fill="currentColor" strokeWidth={1.5} />
          </div>
          {scope && <Badge variant="outline">{scope.label}</Badge>}
        </div>

        <h3 className="line-clamp-2 min-h-11 text-lg font-extrabold">{note.title}</h3>
        <p className="mt-3 text-sm text-muted-foreground">{formatDate(note.createdAt)}</p>

        {note.visibility === "global" && (
          <p className="mt-3 text-xs font-semibold text-muted-foreground">Author: {note.author}</p>
        )}

        {note.visibility === "group" && (
          <p className="mt-3 text-xs font-semibold text-muted-foreground">Group: {groupName(note.groupId)}</p>
        )}

        <div className="mt-4 flex flex-wrap gap-2 text-sm">
          <Badge variant="secondary">{note.subject}</Badge>
          <Badge variant="outline">{note.semester}</Badge>
          <Badge variant="outline">{note.category}</Badge>
        </div>

        <div className="mt-7 flex justify-center gap-3">
          <Button type="button" variant="outline" size="sm" onClick={() => setSelectedNote(note)}>
            <Eye size={14} /> View Folder
          </Button>
          {note.userId === getUserId() && (
            <Button type="button" variant="outline" size="sm" onClick={() => openShareDialog(note)}>
              <Share2 size={14} /> Share
            </Button>
          )}
          <Button type="button" variant="outline" size="sm" className="border-red-200 text-destructive hover:bg-red-50" onClick={() => handleNoteDelete(note.id)}>
            <Trash2 size={14} />
          </Button>
        </div>
      </Card>
    );
  };

  const renderResourceTile = ({ icon, title, date, onClick }) => (
    <button
      type="button"
      onClick={onClick}
      className="flex min-h-40 flex-col items-center justify-center rounded-xl border border-border bg-white p-4 text-center shadow-sm transition hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-soft focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <div className="mb-4 flex h-14 w-14 items-center justify-center">
        {icon}
      </div>
      <h4 className="line-clamp-2 min-h-10 text-sm font-semibold leading-5">{title}</h4>
      <p className="mt-3 text-xs text-muted-foreground">{date}</p>
    </button>
  );

  const renderShareDialog = () => shareNote ? (
    <ShareFolderDialog key={shareNote.id} folder={shareNote} onClose={() => setShareNote(null)} />
  ) : null;

  const renderFolderView = (note) => {
    const resources = getFolderResources(note);
    return (
      <div>
        <button
          type="button"
          className="mb-6 flex items-center gap-2 text-sm font-bold text-primary"
          onClick={() => setSelectedNote(null)}
        >
          <ArrowLeft size={17} /> Back to folders
        </button>

        <Card className="mb-6 bg-card p-6 shadow-soft">
          <div className="flex flex-col gap-5 md:flex-row md:items-start md:justify-between">
            <div className="flex gap-4">
              <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-2xl bg-secondary text-primary">
                <Folder size={48} fill="currentColor" strokeWidth={1.5} />
              </div>
              <div>
                <h1 className="text-3xl font-extrabold tracking-tight">{note.title}</h1>
                <p className="mt-2 text-sm text-muted-foreground">
                  This folder contains uploaded engineering documents and lab resources.
                </p>
                <div className="mt-4 flex flex-wrap gap-2">
                  <Badge variant="secondary">{note.subject}</Badge>
                  <Badge variant="outline">{note.semester}</Badge>
                  <Badge variant="outline">{note.category}</Badge>
                  <Badge variant="outline">{noteScopes.find((scope) => scope.id === note.visibility)?.label || "Private"}</Badge>
                  <Badge variant="outline">{resources.total} items inside</Badge>
                </div>
                {note.visibility === "global" && (
                  <p className="mt-3 text-xs font-semibold text-muted-foreground">Author: {note.author}</p>
                )}
                {note.visibility === "group" && (
                  <p className="mt-3 text-xs font-semibold text-muted-foreground">Group: {groupName(note.groupId)}</p>
                )}
              </div>
            </div>
            {note.userId === getUserId() && (
              <Button type="button" variant="outline" onClick={() => openShareDialog(note)}>
                <Share2 size={16} /> Share
              </Button>
            )}
          </div>
        </Card>

        <div className="space-y-6">
          <section className="space-y-6">
            <Card className="bg-white p-6 shadow-soft">
              <div className="mb-5 flex items-center justify-between gap-4">
                <p className="text-sm font-semibold text-muted-foreground">{resources.total} documents found</p>
                <button type="button" className="rounded-full p-2 text-muted-foreground hover:bg-muted" aria-label="Refresh folder">
                  <RefreshCw size={18} />
                </button>
              </div>
              {resources.documents.length > 0 ? (
                <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
                  {resources.documents.map((document) => renderResourceTile({
                    icon: getDocumentIcon(document, 42),
                    title: displayFileName(document),
                    date: formatDate(note.createdAt),
                    onClick: () => openDocumentPreview(document),
                  }))}
                </div>
              ) : (
                <div className="rounded-xl border border-dashed border-border bg-muted p-8 text-center text-sm text-muted-foreground">
                  No documents uploaded yet.
                </div>
              )}
            </Card>
          </section>
        </div>
      </div>
    );
  };

  const renderNotes = () => (
    <div>
      {selectedNote ? (
        <>
          {renderFolderView(selectedNote)}
          {renderShareDialog()}
        </>
      ) : (
      <>
      <PageHeader title="My Notes" description="Open Global, Private, Shared, or Group note folders" />

      <div className="mb-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {noteScopes.map(({ id, label, description, Icon }) => {
          const active = activeNoteScope === id;
          return (
            <button
              key={id}
              type="button"
              onClick={() => setActiveNoteScope(id)}
              className={`rounded-2xl border p-5 text-left transition hover:-translate-y-0.5 ${
                active ? "border-primary bg-primary text-primary-foreground shadow-soft" : "border-border bg-white shadow-sm hover:bg-card"
              }`}
            >
              <div className="flex items-start justify-between gap-4">
                <div className={`flex h-12 w-12 items-center justify-center rounded-xl ${active ? "bg-white/15" : "bg-secondary text-primary"}`}>
                  <Icon size={23} />
                </div>
                <span className={`rounded-full px-3 py-1 text-sm font-extrabold ${active ? "bg-white/15" : "bg-muted text-foreground"}`}>
                  {noteScopeCounts[id] || 0}
                </span>
              </div>
              <h2 className="mt-4 text-lg font-extrabold">{label}</h2>
              <p className={`mt-2 text-sm leading-6 ${active ? "text-primary-foreground/75" : "text-muted-foreground"}`}>{description}</p>
            </button>
          );
        })}
      </div>

      <Card className="mb-6 bg-card p-5 shadow-none">
        <div className="grid gap-4 md:grid-cols-3">
          <label className="space-y-2">
            <span className="text-xs font-bold">Semester</span>
            <select value={filters.semester} onChange={(event) => setFilters((prev) => ({ ...prev, semester: event.target.value }))} className="h-10 w-full rounded-lg border border-input bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-ring">
              <option value="all">All semesters</option>
              {semesters.map((semester) => <option key={semester}>{semester}</option>)}
            </select>
          </label>
          <label className="space-y-2">
            <span className="text-xs font-bold">Subject</span>
            <select value={filters.subject} onChange={(event) => setFilters((prev) => ({ ...prev, subject: event.target.value }))} className="h-10 w-full rounded-lg border border-input bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-ring">
              <option value="all">All subjects</option>
              {allSubjects.map((subject) => <option key={subject}>{subject}</option>)}
            </select>
          </label>
          <label className="space-y-2">
            <span className="text-xs font-bold">Category</span>
            <select value={filters.category} onChange={(event) => setFilters((prev) => ({ ...prev, category: event.target.value }))} className="h-10 w-full rounded-lg border border-input bg-white px-3 text-sm outline-none focus:ring-2 focus:ring-ring">
              <option value="all">All categories</option>
              {categories.map((category) => <option key={category}>{category}</option>)}
            </select>
          </label>
        </div>
        <div className="mt-4 flex items-center justify-between text-sm text-muted-foreground">
          <span>{scopeNotes.length} folders found in {noteScopes.find((scope) => scope.id === activeNoteScope)?.label}</span>
          <button type="button" className="flex items-center gap-2 font-semibold text-primary" onClick={() => setFilters({ semester: "all", subject: "all", category: "all" })}>
            <RefreshCw size={15} /> Reset filters
          </button>
        </div>
      </Card>

      {scopeNotes.length === 0 ? (
        <Card className="grid min-h-64 place-items-center bg-white p-8 text-center">
          <div>
            <FileText className="mx-auto text-muted-foreground" size={34} />
            <h3 className="mt-4 text-xl font-extrabold">No folders found</h3>
            <p className="mt-2 text-sm text-muted-foreground">Create a folder, share a private folder, or change the filters.</p>
          </div>
        </Card>
      ) : (
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {scopeNotes.map(renderFolderCard)}
        </div>
      )}
      {renderShareDialog()}
      </>
      )}
    </div>
  );

  const recentNotes = useMemo(
    () =>
      [...filteredNotes]
        .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
        .slice(0, 3),
    [filteredNotes]
  );

  const renderGroups = () => (
    <div>
      <PageHeader
        title="Groups"
        description="Create study groups and see who joined each group"
        action={
          <Button type="button" onClick={() => setShowGroupForm(true)}>
            <UserPlus size={16} /> Create Group
          </Button>
        }
      />

      {showGroupForm && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-black/45 px-4 py-8">
          <Card className="max-h-[90vh] w-full max-w-2xl overflow-y-auto bg-white p-6 shadow-soft">
            <div className="mb-5 flex items-start justify-between gap-4">
              <div>
                <h2 className="text-xl font-extrabold tracking-tight">Create Group</h2>
                <p className="mt-1 text-sm text-muted-foreground">Add members by registration number.</p>
              </div>
              <button
                type="button"
                onClick={resetGroupForm}
                className="rounded-full p-2 text-muted-foreground hover:bg-muted"
                aria-label="Close create group dialog"
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleCreateGroup} className="space-y-5">
              <label className="block space-y-2">
                <span className="text-xs font-bold uppercase text-muted-foreground">Group Name *</span>
                <Input
                  value={newGroupName}
                  onChange={(event) => setNewGroupName(event.target.value)}
                  placeholder="e.g., OOP Revision Group"
                  required
                  className="bg-white"
                  autoFocus
                />
              </label>

              <div className="relative space-y-2">
                <label className="block space-y-2">
                  <span className="text-xs font-bold uppercase text-muted-foreground">Registration No</span>
                  <Input
                    value={groupRegistrationSearch}
                    onChange={(event) => setGroupRegistrationSearch(event.target.value)}
                    placeholder="Enter registration number"
                    className="bg-white"
                  />
                </label>

                {groupUserOptions.length > 0 && (
                  <div className="absolute left-0 right-0 top-full z-10 mt-2 overflow-hidden rounded-lg border border-border bg-white shadow-soft">
                    {groupUserOptions.map((user) => (
                      <button
                        key={user.id}
                        type="button"
                        onClick={() => addGroupUser(user)}
                        className="flex w-full items-center gap-3 px-3 py-3 text-left hover:bg-muted"
                      >
                        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-secondary text-xs font-extrabold text-primary">
                          {userInitials(user.username || user.name)}
                        </div>
                        <div className="min-w-0">
                          <p className="truncate text-sm font-extrabold">{user.username || user.name}</p>
                          <p className="text-xs font-semibold text-muted-foreground">{user.registrationNumber || user.registrationNo}</p>
                        </div>
                      </button>
                    ))}
                  </div>
                )}
              </div>

              <div className="space-y-3 pt-1">
                {selectedGroupUsers.length === 0 ? (
                  <div className="rounded-lg border border-dashed border-border bg-muted/40 px-4 py-6 text-center text-sm text-muted-foreground">
                    No members selected yet.
                  </div>
                ) : (
                  selectedGroupUsers.map((user) => (
                    <div key={user.id} className="flex items-center gap-3 rounded-lg border border-border bg-white p-3">
                      <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-md bg-secondary text-sm font-extrabold text-primary">
                        {userInitials(user.username || user.name)}
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-extrabold">{user.username || user.name}</p>
                        <p className="text-xs font-semibold text-muted-foreground">{user.registrationNumber || user.registrationNo}</p>
                      </div>
                      <button
                        type="button"
                        onClick={() => removeGroupUser(user.id)}
                        className="rounded-full p-2 text-muted-foreground hover:bg-muted"
                        aria-label={`Remove ${user.username || user.name}`}
                      >
                        <X size={17} />
                      </button>
                    </div>
                  ))
                )}
              </div>

              <div className="flex justify-end gap-3 pt-2">
                <Button type="button" variant="outline" onClick={resetGroupForm}>
                  Cancel
                </Button>
                <Button type="submit">Create Group</Button>
              </div>
            </form>
          </Card>
        </div>
      )}

      <div className="grid gap-5 lg:grid-cols-2">
        {groups.map((group) => (
          <Card key={group.id} className="bg-white p-6 shadow-soft">
            <div className="flex items-start justify-between gap-4">
              <div className="flex gap-4">
                <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-secondary text-primary">
                  <Users size={27} />
                </div>
                <div>
                  <h2 className="text-xl font-extrabold">{group.name}</h2>
                  <p className="mt-2 text-sm text-muted-foreground">Admin: {group.admin}</p>
                </div>
              </div>
              <Badge variant="outline">{group.members.length} members</Badge>
            </div>

            <div className="mt-5 flex flex-wrap gap-2">
              {group.members.map((member) => (
                <span key={member} className="rounded-full bg-muted px-3 py-1 text-sm font-semibold">
                  {member}
                </span>
              ))}
            </div>
          </Card>
        ))}
      </div>
    </div>
  );

  const renderDashboard = () => {
    const totalDocuments = noteViews.reduce((count, note) => count + getFolderResources(note).total, 0);
    const sharedFolders = noteViews.filter((note) => note.visibility === "global" || note.visibility === "group" || note.sharedByMe);
    const statCards = [
      { label: "Note Folders", value: noteViews.length || notes.length || 6, detail: `${totalDocuments || 17} uploaded documents`, Icon: Folder },
      { label: "Shared Notes", value: sharedFolders.length || 4, detail: "Global and group folders", Icon: Share2 },
      { label: "Study Groups", value: groups.length || 2, detail: "Classmate collaboration", Icon: Users },
      { label: "Subjects", value: allSubjects.length || 6, detail: "Organized by semester", Icon: Grid3X3 },
    ];

    return (
      <div>
        <PageHeader title="Dashboard" description="Keep lecture notes, shared folders, and study-group resources organized by subject." />

        <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-4">
          {statCards.map(({ label, value, detail, Icon }) => (
            <Card key={label} className="bg-card p-6 shadow-soft">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="text-sm font-semibold text-muted-foreground">{label}</p>
                  <p className="mt-2 text-4xl font-extrabold tracking-tight">{value}</p>
                  <p className="mt-2 text-xs font-semibold text-muted-foreground">{detail}</p>
                </div>
                <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-secondary text-primary">
                  <Icon size={24} />
                </div>
              </div>
            </Card>
          ))}
        </div>

        <section className="mt-9">
          <div className="mb-5 flex items-center justify-between gap-4">
            <h2 className="text-2xl font-extrabold tracking-tight">Recent Study Folders</h2>
            <Button type="button" onClick={() => window.dispatchEvent(new CustomEvent("leckeeper:navigate-add"))}>
              + Add New Note
            </Button>
          </div>

          {recentNotes.length > 0 ? (
            <div className="grid gap-5 lg:grid-cols-3">
              {recentNotes.map((note) => {
                const scope = noteScopes.find((item) => item.id === note.visibility);
                const accessText =
                  note.visibility === "group"
                    ? `Shared in ${groupName(note.groupId)}`
                    : note.visibility === "global"
                    ? "Visible to all students"
                    : note.sharedByMe
                    ? "Shared by you"
                    : "Private folder";

                return (
                  <Card key={note.id} className="bg-card p-6 shadow-soft">
                    <div className="flex h-full flex-col">
                      <div className="mb-4 flex items-start justify-between gap-3">
                        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-secondary text-primary">
                          <FileText size={21} />
                        </div>
                        <Badge variant="outline">{scope?.label || "Private"}</Badge>
                      </div>
                      <h3 className="line-clamp-2 min-h-14 text-lg font-extrabold leading-7">{note.title}</h3>
                      <p className="mt-3 line-clamp-2 text-sm leading-6 text-muted-foreground">{note.content}</p>
                      <div className="mt-5 flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                        <span className="rounded-full bg-muted px-3 py-1">{note.subject || "General"}</span>
                        <span className="flex items-center gap-1">
                          <Calendar size={14} />
                          {formatDate(note.createdAt)}
                        </span>
                      </div>
                      <div className="mt-auto flex flex-wrap items-center justify-between gap-3 pt-5 text-xs font-semibold text-muted-foreground">
                        <span>{accessText}</span>
                        <span className="rounded-full bg-secondary px-3 py-1 text-primary">
                          {getFolderResources(note).total} docs
                        </span>
                      </div>
                    </div>
                  </Card>
                );
              })}
            </div>
          ) : (
            <Card className="bg-card p-6 shadow-soft">
              <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <h3 className="text-lg font-extrabold">Semester 4 Revision Folder</h3>
                  <div className="mt-5 flex flex-wrap items-center gap-4 text-sm text-muted-foreground">
                    <span className="rounded-full bg-muted px-3 py-1">Operating Systems</span>
                    <span>Jun 1, 2026</span>
                    <span className="rounded-full bg-secondary px-3 py-1 text-xs font-semibold text-primary">Shared with group</span>
                  </div>
                </div>
                <div className="flex gap-2">
                  <Button type="button" variant="outline" size="sm">View</Button>
                </div>
              </div>
            </Card>
          )}
        </section>
      </div>
    );
  };

  if (loading && activeSection !== "search") {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <div className="h-9 w-9 animate-spin rounded-full border-4 border-secondary border-t-primary" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl">
      {error && (
        <div className="mb-6 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          {error}
        </div>
      )}

      <DocumentPreviewDialog
        document={previewDocumentFile}
        preview={documentPreview}
        downloadUrl={previewDocumentFile?.id ? getDocumentDownloadUrl(previewDocumentFile.id) : ""}
        loading={documentPreviewLoading}
        error={documentPreviewError}
        onClose={closeDocumentPreview}
      />

      {activeSection === "dashboard" && <DashboardView>{renderDashboard()}</DashboardView>}
      {activeSection === "search" && renderSearch()}
      {activeSection === "notes" && <NotesView>{renderNotes()}</NotesView>}
      {activeSection === "add" && <AddNoteDialog>{renderAddNote()}</AddNoteDialog>}
      {activeSection === "groups" && <GroupsView>{renderGroups()}</GroupsView>}
      {activeSection === "subjects" && <SubjectsView>{renderSubjects()}</SubjectsView>}
      {activeSection === "folders" && <FoldersView>{renderNotes()}</FoldersView>}
      {activeSection === "settings" && (
        <div>
          <PageHeader title="Settings" description="Account settings will be added later" />
          <Card className="max-w-xl bg-white p-6 text-sm text-muted-foreground">
            No settings are available yet.
          </Card>
        </div>
      )}
    </div>
  );
};

export default MainContent;

