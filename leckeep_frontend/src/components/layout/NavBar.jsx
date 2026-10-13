import { useEffect, useState } from "react";
import { Bell, Search } from "lucide-react";
import { Input } from "../ui/Input";
import { apiFetch, getUserId } from "../../lib/apiClient";

const NavBar = ({ onSearch, searchQuery, onSearchInput }) => {
  const [user, setUser] = useState(null);

  useEffect(() => {
    if (!searchQuery.trim()) return;
    const timer = setTimeout(() => onSearch(searchQuery), 300);
    return () => clearTimeout(timer);
  }, [searchQuery, onSearch]);

  useEffect(() => {
    const fetchUser = async () => {
      const userId = getUserId();
      if (!userId) return;

      try {
        const userData = await apiFetch(`/users/${userId}`);
        setUser(userData?.data || userData);
      } catch (error) {
        console.error("Failed to fetch user data:", error);
      }
    };

    fetchUser();
  }, []);

  const handleSearch = (event) => {
    const query = event.target.value;
    onSearchInput(query);
  };

  const username = user?.username || "CE Student";
  const initials = username.slice(0, 2).toUpperCase();

  return (
    <header className="fixed left-0 right-0 top-0 z-30 border-b border-border bg-white lg:left-64">
      <div className="flex h-20 items-center gap-4 px-6 lg:px-8">
        <div className="relative w-full max-w-xl">
          <Search className="absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={searchQuery}
            onChange={handleSearch}
            maxLength={100}
            aria-label="Search academic resources"
            placeholder="Search architecture, Verilog, networks..."
            className="h-12 rounded-lg border-0 bg-[#eef6fb] pl-12 shadow-none"
          />
        </div>

        <div className="ml-auto flex items-center gap-6">
          <button type="button" className="hidden rounded-full p-2 hover:bg-muted sm:block" aria-label="Notifications">
            <Bell size={20} />
          </button>
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-sm font-bold text-primary-foreground">
              {initials}
            </div>
            <span className="hidden text-sm font-semibold sm:inline">{username}</span>
          </div>
        </div>
      </div>
    </header>
  );
};

export default NavBar;
