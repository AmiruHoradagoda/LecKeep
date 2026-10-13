import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { getGroups } from "../groups/groups.api";
import { getUsers } from "../users/users.api";
import { createFolderShare, getFolderShares, revokeFolderShare } from "./folderShares.api";

export default function ShareFolderDialog({ folder, onClose }) {
  const [targetType, setTargetType] = useState("USER");
  const [targetId, setTargetId] = useState("");
  const [shares, setShares] = useState([]);
  const [users, setUsers] = useState([]);
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [sharesLoaded, setSharesLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [choicesError, setChoicesError] = useState("");
  const [message, setMessage] = useState("");

  useEffect(() => {
    let active = true;
    getFolderShares(folder.id).then((data) => { if (active) { setShares(data); setSharesLoaded(true); } })
      .catch((err) => { if (active) setError(err.message); })
      .finally(() => { if (active) setLoading(false); });
    Promise.all([getUsers(), getGroups({ size: 100, scope: "shareable" })])
      .then(([userList, groupPage]) => {
        if (active) { setUsers(userList); setGroups(groupPage?.dataList || []); }
      })
      .catch((err) => { if (active) setChoicesError(`Could not load sharing targets: ${err.message}`); });
    return () => { active = false; };
  }, [folder.id]);

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const share = await createFolderShare(folder.id, {
        targetType, targetId: targetType === "GLOBAL" ? null : targetId, permission: "READ",
      });
      setShares((previous) => [...previous.filter((item) => item.id !== share.id), share]);
      setMessage("Read access shared. Folder visibility is unchanged.");
    } catch (err) { setError(err.message || "Could not share folder."); }
    finally { setBusy(false); }
  };

  const revoke = async (share) => {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await revokeFolderShare(folder.id, share.id);
      setShares((previous) => previous.filter((item) => item.id !== share.id));
      setMessage("Share revoked. Access from visibility or another share still applies.");
    } catch (err) { setError(err.message || "Could not revoke share."); }
    finally { setBusy(false); }
  };

  const targetLabel = (share) => {
    if (share.targetType === "GLOBAL") return "All authenticated users";
    const target = (share.targetType === "USER" ? users : groups).find((item) => item.id === share.targetId);
    return target?.username || target?.name || share.targetId;
  };

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-slate-900/35 p-4 backdrop-blur-md">
      <Card role="dialog" aria-modal="true" aria-labelledby="share-folder-title"
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto bg-white p-6 shadow-2xl">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h2 id="share-folder-title" className="text-xl font-extrabold">Share Folder</h2>
            <p className="mt-2 text-sm text-muted-foreground">{folder.title}</p>
          </div>
          <Button type="button" variant="ghost" size="icon" onClick={onClose} disabled={busy} aria-label="Close sharing dialog"><X size={18} /></Button>
        </div>
        <p className="mb-4 text-sm text-muted-foreground">
          Visibility: {folder.visibility?.toUpperCase() || "PRIVATE"}. Explicit shares leave visibility unchanged.
          Recipients can find this folder in search. Revoking a share only removes that grant of access.
        </p>
        {error && <p role="alert" className="mb-3 text-sm text-destructive">{error}</p>}
        {choicesError && <p role="alert" className="mb-3 text-sm text-destructive">{choicesError}</p>}
        {message && <p role="status" className="mb-3 text-sm text-primary">{message}</p>}
        <form onSubmit={submit} className="space-y-4">
          <label className="block space-y-2">
            <span className="text-sm font-bold">Share with</span>
            <select value={targetType} onChange={(event) => { setTargetType(event.target.value); setTargetId(""); }}
              disabled={busy} className="h-10 w-full rounded-lg border border-input bg-white px-3 text-sm">
              <option value="USER">User</option><option value="GROUP">Group</option><option value="GLOBAL">Global authenticated users</option>
            </select>
          </label>
          {targetType !== "GLOBAL" && (
            <label className="block space-y-2">
              <span className="text-sm font-bold">{targetType === "USER" ? "Select user" : "Select group you own or administer"}</span>
              <select value={targetId} onChange={(event) => setTargetId(event.target.value)} required disabled={busy}
                className="h-10 w-full rounded-lg border border-input bg-white px-3 text-sm">
                <option value="">Choose a target</option>
                {(targetType === "USER" ? users : groups).map((item) => (
                  <option key={item.id} value={item.id}>{item.username || item.name}{item.registrationNumber ? ` (${item.registrationNumber})` : ""}</option>
                ))}
              </select>
            </label>
          )}
          <label className="block space-y-2">
            <span className="text-sm font-bold">Permission</span>
            <select className="h-10 w-full rounded-lg border border-input bg-white px-3 text-sm" value="READ" disabled>
              <option value="READ">READ — view folder and documents</option>
            </select>
          </label>
          <p className="text-xs text-muted-foreground">Editing, upload, and deletion remain owner-only. WRITE shares currently grant read access only.</p>
          <Button type="submit" disabled={!sharesLoaded || loading || busy || (targetType !== "GLOBAL" && !targetId)}>Share Folder</Button>
        </form>
        <div className="mt-6 border-t border-border pt-4">
          <h3 className="mb-3 font-bold">Current explicit shares</h3>
          {loading ? <p role="status">Loading shares…</p> : !sharesLoaded ? <p className="text-sm text-muted-foreground">Close and reopen this dialog to retry loading shares.</p> : shares.length === 0 ? <p className="text-sm text-muted-foreground">No explicit shares.</p> : (
            <ul className="space-y-3">
              {shares.map((share) => (
                <li key={share.id} className="flex items-center justify-between gap-3 rounded-lg border border-border p-3">
                  <div className="min-w-0 break-words text-sm"><p className="font-semibold">{targetLabel(share)}</p><p className="text-muted-foreground">{share.targetType} · {share.permission}{share.permission === "WRITE" ? " (read access only)" : ""}</p></div>
                  <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => revoke(share)}>Revoke</Button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </Card>
    </div>
  );
}
