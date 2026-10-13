import { useCallback, useEffect, useRef, useState } from "react";
import NavBar from "../components/layout/NavBar";
import SideBar from "../components/layout/SideBar";
import AuthForms from "../features/auth/AuthForms";
import MainContent from "../features/workspace/MainContent";
import { useAuth } from "../hooks/useAuth";
import { apiFetch } from "../lib/apiClient";

const App = () => {
  const { isAuthenticated, markAuthenticated, logout } = useAuth();
  const [searchResults, setSearchResults] = useState(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState("");
  const searchRequest = useRef(null);
  const [activeSection, setActiveSection] = useState("dashboard");

  useEffect(() => {
    const openAddNote = () => setActiveSection("add");
    window.addEventListener("leckeeper:navigate-add", openAddNote);
    return () => window.removeEventListener("leckeeper:navigate-add", openAddNote);
  }, []);

  const handleLogout = () => {
    searchRequest.current?.abort();
    logout();
    setSearchQuery("");
    setSearchLoading(false);
    setSearchError("");
    setSearchResults(null);
    setActiveSection("dashboard");
  };

  const handleSearchInput = useCallback((value) => {
    searchRequest.current?.abort();
    setSearchQuery(value);
    setSearchResults(null);
    setSearchError("");
    setSearchLoading(Boolean(value.trim()));
    setActiveSection((section) => value.trim() ? "search" : section === "search" ? "notes" : section);
  }, []);

  const handleSearch = useCallback(async (searchTxt) => {
    if (!searchTxt.trim()) return;
    searchRequest.current?.abort();
    const controller = new AbortController();
    searchRequest.current = controller;
    try {
      const response = await apiFetch("/search", {
        signal: controller.signal,
        params: {
          q: searchTxt.trim(),
          scope: "all",
        },
      });
      if (controller.signal.aborted) return;
      const data = response?.data ?? response;
      if (!Array.isArray(data)) throw new Error("Server returned invalid search results.");
      setSearchResults(data);
    } catch (error) {
      if (!controller.signal.aborted) setSearchError(error.message || "Search failed.");
    } finally {
      if (!controller.signal.aborted) setSearchLoading(false);
    }
  }, []);

  useEffect(() => () => searchRequest.current?.abort(), []);

  const navigate = (section) => {
    searchRequest.current?.abort();
    setSearchQuery("");
    setSearchResults(null);
    setSearchLoading(false);
    setSearchError("");
    setActiveSection(section);
  };

  if (!isAuthenticated) {
    return (
      <AuthForms
        onLoginSuccess={markAuthenticated}
        onRegisterSuccess={markAuthenticated}
      />
    );
  }

  return (
    <div className="min-h-screen bg-white">
      <NavBar onSearch={handleSearch} searchQuery={searchQuery} onSearchInput={handleSearchInput} />
      <SideBar activeSection={activeSection} onSectionChange={navigate} onLogout={handleLogout} />
      <main className="px-6 pb-10 pt-28 lg:ml-64 lg:px-8">
        <MainContent activeSection={activeSection} searchResults={searchResults}
          searchQuery={searchQuery} searchLoading={searchLoading} searchError={searchError} onNavigate={navigate} />
      </main>
    </div>
  );
};

export default App;


