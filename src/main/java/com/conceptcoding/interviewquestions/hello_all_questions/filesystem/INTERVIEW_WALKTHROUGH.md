# In-Memory File System — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)
**Source method:** Hello Interview *Delivery Framework* applied to the *File System* problem breakdown.

> File System is the most algorithmically rich of the common LLD problems — tree manipulation, path resolution, cycle detection on `move`, and the parent-pointer vs stored-path architectural choice. Get those four right and you're senior. The natural pattern here is **Composite** (FileSystemEntry / File / Folder) — name it explicitly, even though it's not in Hello Interview's canonical 8.

> Hello Interview labels this *medium*, but it's the **hardest** of the common LLD problems. Apply the 5-step framework strictly or you'll run out of time on the implementation.

---

## Time budget (45 min)

| Step | Activity                                                              | Budget   | Cumulative |
| ---- | --------------------------------------------------------------------- | -------- | ---------- |
| 1    | Requirements                                                          | ~5 min   | 5          |
| 2    | Entities & Relationships                                              | ~4 min   | 9          |
| 3    | Class Design (state + behavior, the FileSystemEntry abstraction)      | ~10 min  | 19         |
| 4    | Implementation (`createFile` / `move` / `rename` + path helpers + dry run) | ~18 min | 37 |
| 5    | Extensibility (thread-safety, search index)                            | ~7 min   | 44         |
| —    | Wrap & questions                                                      | ~1 min   | 45         |

Step 4 is the longest because there are 7 public methods + 3 path helpers. Don't spend more than 18 minutes here — focus on `createFile`, `move`, `rename`, and `resolvePath` (the 4 that show the interesting decisions).

Watch the clock at minute **5** (Step 1 done), minute **19** (start coding), minute **37** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

Three pictures unlock the implementation. If you can draw them from memory, the code shape is fixed.

### M1. The tree + Composite shape

```
                  FileSystem
                       |
                  owns | root
                       v
                +--------------+
                |  Folder "/"  |    <-- root, parent = null
                +--------------+
                  /         \
                 /           \
   +-------------+         +-------------+
   | Folder home |         | File rdme   |   <-- leaf (no children)
   +-------------+         +-------------+
         |
         |
   +-------------+
   | Folder user |
   +-------------+
        / \
   +---------+   +-------------+
   | F notes |   | Folder docs |
   +---------+   +-------------+


   abstract FileSystemEntry
   ├── File   (leaf,  has content,        isDirectory() = false)
   └── Folder (composite, has children,   isDirectory() = true)

   This is the textbook Composite pattern: callers treat any FileSystemEntry
   the same way; the type-specific behavior is in the subclass.
```

### M2. Parent pointers + bidirectional consistency

```
   Each Folder    knows its children by name (Map<String, FileSystemEntry>).
   Each FSEntry   knows its parent Folder (or null if root).

       ┌──────── parent ────────┐
       v                        |
   Folder "home" ── children ── ▶  Folder "user", File "notes.txt"
                                      │
                                      │ parent
                                      ▼
                                  Folder "home"


   addChild(entry):
       1. children.put(entry.getName(), entry)
       2. entry.setParent(this)        <-- both directions in sync

   removeChild(name):
       1. entry = children.remove(name)
       2. entry.setParent(null)        <-- detach the back-reference too

   getPath():    walk parent pointers up to root, building string lazily
                 -> rename a folder, ALL descendants' paths auto-update
                    (this is the whole reason we use parent pointers over
                    stored path strings)
```

**Senior soundbite (memorize):** *"I store parent pointers, not full paths. With parent pointers, renaming `/home` to `/users` instantly updates the path of every descendant — `getPath()` recomputes from the tree. With stored paths, you'd have to walk every descendant and rewrite their strings, which is O(N) per rename and bug-prone."*

### M3. The rename-via-map-key dance

```
   Folder.children is a Map<String, FileSystemEntry>.  The KEY is the entry's name.
   So calling entry.setName("newName") alone leaves it orphaned under the OLD key.

   Correct rename sequence:

        +--------------------------------------+
        |  1. entry = parent.removeChild(old)  |  <-- remove keyed by old name
        |  2. entry.setName(new)               |  <-- mutate the name
        |  3. parent.addChild(entry)           |  <-- re-add keyed by new name
        +--------------------------------------+

   Same dance applies to `move` (just with different src/dest parents).
   Skip step 1, and you have stale data in the source map; skip step 3 and
   the entry is unreachable.
```

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener)
> "File systems are familiar territory, so my mental model might not match yours. Let me clarify scope and rules before designing."

### Probe the 4 themes

| Theme               | Question to ask                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Primary capabilities| "Hierarchy: single Unix root, or Windows-style drives? Operations — create/delete/list/get/rename/move?"     |
| Rules / completion  | "Files store actual content (strings)? Folders contain mixed files + folders? Deleting a folder also deletes its subtree?" |
| Error handling      | "Invalid path / missing parent / name collision / delete root — all throw specific exceptions?"              |
| Scope boundaries    | "Out: search, relative paths, permissions, timestamps, symlinks, persistence, UI — confirm?"                 |

### What to write on the board

```
Functional Requirements
1. Hierarchical file system with a single root "/".
2. Files store string content; folders contain mixed children (files OR folders).
3. Public operations: createFile, createFolder, delete, list, get, rename, move.
4. Absolute paths only (Unix-style: /home/user/notes.txt).
5. Every entry must be able to report its full path.
6. Distinct, specific exceptions for: invalid path, not found, already exists, not-a-directory.
7. Scale: tens of thousands of entries, deep hierarchies stay responsive.

Out of Scope
- Search functionality (Step 5)
- Relative paths (../ ./)
- Permissions, ownership, timestamps
- File-type-specific behavior
- Persistence / disk storage
- Symbolic links / hard links
- UI / rendering
- Thread safety (Step 5)
```

### Close the step
> "Does this match what you had in mind? Anything you'd add before I move to entities?"

---

## STEP 2 — Entities & Relationships (~4 min)

### What to say out loud
> "Three core entities: **FileSystem** (orchestrator + facade — owns root, parses paths, exposes the public API), **Folder** (composite — has a name + a map of children), **File** (leaf — has a name + content). They form a tree, and I want to call out that this is naturally the **Composite pattern** — both File and Folder are 'entries' that share an identity. I'll extract that shared identity into an abstract `FileSystemEntry` in Step 3."

### Why no `Path` class
> "Paths look like an entity at first — they appear all over the requirements — but a path has no state and no rules. It's just a string we parse into tree-walking instructions. So `String path` as an argument, not its own class."

### What to write on the board

```
Entities
- FileSystem          (orchestrator + facade: createFile, delete, move, rename, list, get)
- FileSystemEntry     (abstract base: name + parent + getPath + isDirectory)   <-- shared abstraction
   ├── File           (leaf: content)
   └── Folder         (composite: Map<String, FileSystemEntry> children)

Relationships
- FileSystem owns      Folder (root)
- Folder    contains   Map<String, FileSystemEntry> children
- Every FSEntry        knows its parent Folder (or null for root)
                       ^ this back-pointer is what makes getPath() / rename / move cheap
```

> **Composite vs HI's canonical 8:** Hello Interview's canonical 8 doesn't include Composite. But it's the right name for what we're building, and any senior interviewer will recognize it. Say "*the natural pattern here is Composite*" once, then move on — don't oversell.

### Diagram — boxes and arrows

```
                     +-------------------------+
                     |       FileSystem        |    <- orchestrator + facade
                     | createFile, delete, ... |
                     +-------------------------+
                                |
                          owns  | (root)
                                v
                         +-------------+
                         | Folder "/"  |
                         +-------------+
                         /             \
                  children            children
                       /                 \
                +----------+         +-----------+
                | Folder   |         |   File    |
                +----------+         +-----------+
                     |                    ^
                 children                 |
                     v                    |
                ... (recursive)       (leaf — no children)


   Composite contract — abstract FileSystemEntry:
       getName, setName, getParent, setParent, getPath, isDirectory
   File adds: content
   Folder adds: children (Map<String, FileSystemEntry>)
```

---

## STEP 3 — Class Design (~10 min)

### Work top-down: FileSystem → FileSystemEntry → File → Folder.

### FileSystem — state ↔ requirement table

| Requirement                              | State FileSystem must own                              |
| ---------------------------------------- | ------------------------------------------------------ |
| Single root directory                    | `Folder root` (named "/")                              |

That's it — `FileSystem` has exactly one field. Path parsing is *derived* from root.

### FileSystem — behavior table

| Need from requirements              | Method                                                |
| ----------------------------------- | ----------------------------------------------------- |
| Create files at a path              | `File createFile(String path, String content)`        |
| Create folders at a path            | `Folder createFolder(String path)`                    |
| Remove entries                      | `void delete(String path)`                            |
| List contents of a folder           | `List<FileSystemEntry> list(String path)`             |
| Resolve a path to its entry         | `FileSystemEntry get(String path)`                    |
| Rename in place                     | `void rename(String path, String newName)`            |
| Move between locations              | `void move(String srcPath, String destPath)`          |
| Path parsing                        | Private helpers: `resolvePath`, `resolveParent`, `extractName` |

### FileSystemEntry — outline (abstract, the Composite base)

```java
public abstract class FileSystemEntry {
    private String name;
    private Folder parent;

    public String getName()                { return name; }
    public void   setName(String name)     { this.name = name; }
    public Folder getParent()              { return parent; }
    public void   setParent(Folder parent) { this.parent = parent; }

    public abstract boolean isDirectory();

    // Walks up to root, building the path lazily.
    public String getPath() {
        if (parent == null) return name;
        String parentPath = parent.getPath();
        return parentPath.equals("/") ? "/" + name : parentPath + "/" + name;
    }
}
```

### File / Folder — outlines

```java
public class File extends FileSystemEntry {
    private String content;
    // ctor, getContent, setContent
    @Override public boolean isDirectory() { return false; }
}

public class Folder extends FileSystemEntry {
    private final Map<String, FileSystemEntry> children = new LinkedHashMap<>();
    @Override public boolean isDirectory() { return true; }

    public boolean         addChild(FileSystemEntry e);     // also sets e.parent = this
    public FileSystemEntry removeChild(String name);        // also clears e.parent = null
    public FileSystemEntry getChild(String name);
    public boolean         hasChild(String name);
    public List<FileSystemEntry> getChildren();             // defensive copy
}
```

### Diagram — class cards

```
+--------------------------+   +-------------------------+   +--------------------------+
|       FileSystem         |   |     FileSystemEntry     |   |       Folder             |
+--------------------------+   |       (abstract)        |   |   extends FSEntry        |
| - root: Folder           |   +-------------------------+   +--------------------------+
+--------------------------+   | - name: String          |   | - children:              |
| + createFile(p, c): File |   | - parent: Folder        |   |   Map<String, FSEntry>  |
| + createFolder(p): Folder|   +-------------------------+   +--------------------------+
| + delete(p)              |   | + getName / setName     |   | + addChild(e): bool      |
| + list(p): List<FSEntry> |   | + getParent / setParent |   | + removeChild(n): FSEntry|
| + get(p): FSEntry        |   | + getPath(): String     |   | + getChild(n): FSEntry   |
| + rename(p, n)           |   | + isDirectory():        |   | + hasChild(n): bool      |
| + move(src, dest)        |   |     abstract bool       |   | + getChildren(): List    |
+--------------------------+   +-------------------------+   | + isDirectory(): true    |
                                       ^                     +--------------------------+
                                       |
                                       | extends
                                       |
                            +---------------------------+
                            |          File             |
                            |     extends FSEntry       |
                            +---------------------------+
                            | - content: String         |
                            +---------------------------+
                            | + getContent / setContent |
                            | + isDirectory(): false    |
                            +---------------------------+

FileSystem --owns--> Folder (root)         Folder --contains--> children: Map<name, FSEntry>
FSEntry    --back-refs--> Folder (parent)
```

### Why the FileSystemEntry abstraction (the senior signal)

> *"Before extracting `FileSystemEntry`, File and Folder duplicated `name`, `parent`, `getPath()`, and the back-pointer logic. The duplication isn't coincidental — both ARE nodes in the same tree, they just differ in 'has children' vs 'has content'. Extracting the abstract base captures that shared identity and gives me a real type for `Folder.children`. Without it, the map would have to be `Map<String, Object>` and every read would need a cast."*

### Principle to verbalize — Information Expert
> "Path-string parsing lives on `FileSystem` because only `FileSystem` knows about paths. Children manipulation lives on `Folder` because only Folder owns the children map. Path-from-tree computation lives on `FileSystemEntry` because every entry knows its own parent. Three classes, three different reasons to change."

---

## STEP 4 — Implementation (~18 min)

### Open by asking
> "Real Java or pseudo-code? I'll walk through `createFile` first (the simplest CRUD), then the path helpers (which all other methods reuse), then `move` (the most complex), then dry-run a scenario."

### 4.1 `createFile` — flow + code

```
   createFile(path, content)
        |
        v
   +----------------------------+
   | path == "/" ?              |--yes--> InvalidPathException
   +----------------------------+
                | no
                v
   +----------------------------+
   | parent = resolveParent(p)  |  (throws if any parent component missing)
   | name = extractName(p)      |
   +----------------------------+
                v
   +----------------------------+
   | parent.hasChild(name) ?    |--yes--> AlreadyExistsException
   +----------------------------+
                | no
                v
   +----------------------------+
   | file = new File(name, c)   |
   | parent.addChild(file)      |  (Folder sets file.parent = this)
   +----------------------------+
                v
            return file
```

```java
public File createFile(String path, String content) {
    if (ROOT.equals(path)) throw new InvalidPathException("Cannot create file at root");
    Folder parent = resolveParent(path);
    String fileName = extractName(path);
    if (parent.hasChild(fileName)) {
        throw new AlreadyExistsException("Entry already exists: " + path);
    }
    File file = new File(fileName, content);
    parent.addChild(file);
    return file;
}
```

> **Senior callout:** *"`createFolder` is structurally identical — same guard, same parent resolution, same collision check. Some candidates try to merge them with a boolean `isDir` flag; I'd push back — separate methods are clearer at the API boundary, and the internal duplication is small."*

### 4.2 Path helpers — the entire "string-to-tree" subsystem

These three methods are the secret to keeping the public API readable.

```java
// Walk one component at a time. Throws for null/empty, non-absolute,
// missing component, or hitting a file when more components remain.
private FileSystemEntry resolvePath(String path) {
    if (path == null || path.isEmpty()) throw new InvalidPathException("Path cannot be null/empty");
    if (!path.startsWith(SEPARATOR))    throw new InvalidPathException("Path must be absolute");
    if (ROOT.equals(path))              return root;

    String[] parts = path.substring(1).split(SEPARATOR);
    FileSystemEntry current = root;
    for (String part : parts) {
        if (part.isEmpty())          throw new InvalidPathException("Consecutive slashes");
        if (!current.isDirectory())  throw new NotADirectoryException("Not a dir: " + current.getPath());
        FileSystemEntry child = ((Folder) current).getChild(part);
        if (child == null)            throw new NotFoundException("Path not found: " + path);
        current = child;
    }
    return current;
}

private Folder resolveParent(String path) {
    if (ROOT.equals(path)) throw new InvalidPathException("Root has no parent");
    int lastSlash = path.lastIndexOf(SEPARATOR);
    String parentPath = (lastSlash == 0) ? ROOT : path.substring(0, lastSlash);
    FileSystemEntry parent = resolvePath(parentPath);
    if (!parent.isDirectory()) throw new NotADirectoryException(...);
    return (Folder) parent;
}

private String extractName(String path) {
    return path.substring(path.lastIndexOf(SEPARATOR) + 1);
}
```

> **Senior callout:** *"These three helpers are private and reused by every public method. If I didn't extract them, `createFile`, `createFolder`, `delete`, `rename`, and `move` would each contain a copy of the same path-parsing logic — DRY violation, bug magnet. With the extraction, the public methods stay focused on their actual job."*

### 4.3 `move` — the most interesting method (cycle check!)

```
   move(srcPath, destPath)
        |
        v
   +----------------------------------+
   | srcPath == "/" ?                 |--yes--> InvalidPathException
   +----------------------------------+
                  | no
                  v
   +----------------------------------+
   | entry = srcParent.getChild(...)  |
   | entry == null ?                  |--yes--> NotFoundException
   +----------------------------------+
                  | no
                  v
   +----------------------------------+   <-- the senior bit
   | entry isDirectory AND cursor =   |
   | destParent; walk up via parent — |
   | if we hit `entry`, it's a cycle  |--yes--> InvalidPathException
   +----------------------------------+
                  | no cycle
                  v
   +----------------------------------+
   | destParent.hasChild(destName) ?  |--yes--> AlreadyExistsException
   +----------------------------------+
                  | no
                  v
   +----------------------------------+
   | srcParent.removeChild(srcName)   |
   | entry.setName(destName)          |
   | destParent.addChild(entry)       |  (back-pointer auto-updated)
   +----------------------------------+
```

```java
public void move(String srcPath, String destPath) {
    if (ROOT.equals(srcPath)) throw new InvalidPathException("Cannot move root");

    Folder srcParent = resolveParent(srcPath);
    String srcName = extractName(srcPath);
    FileSystemEntry entry = srcParent.getChild(srcName);
    if (entry == null) throw new NotFoundException("Source not found: " + srcPath);

    Folder destParent = resolveParent(destPath);
    String destName = extractName(destPath);

    // Cycle check — only matters for directories.
    if (entry.isDirectory()) {
        Folder cursor = destParent;
        while (cursor != null) {
            if (cursor == entry) {
                throw new InvalidPathException("Cannot move folder into itself or a descendant");
            }
            cursor = cursor.getParent();
        }
    }

    if (destParent.hasChild(destName)) {
        throw new AlreadyExistsException("Destination already exists: " + destPath);
    }

    srcParent.removeChild(srcName);
    entry.setName(destName);
    destParent.addChild(entry);
}
```

> **Senior callout on cycle detection:** *"If I move `/home` into `/home/user/stuff`, the tree would have `/home` as both ancestor AND descendant of `/home/user` — an impossible loop. I detect this by walking from `destParent` upward via parent pointers; if I ever hit `entry`, the move would create a cycle, reject. This is the **leetcode-y trap** in this problem — easy to miss until your interviewer points to it."*

### 4.4 `rename` — the map-key dance

```java
public void rename(String path, String newName) {
    if (ROOT.equals(path)) throw new InvalidPathException("Cannot rename root");
    if (newName == null || newName.isEmpty() || newName.contains(SEPARATOR)) {
        throw new InvalidPathException("Invalid name: " + newName);
    }
    Folder parent = resolveParent(path);
    String oldName = extractName(path);
    if (!parent.hasChild(oldName))       throw new NotFoundException("Entry not found: " + path);
    if (parent.hasChild(newName))        throw new AlreadyExistsException("Sibling exists: " + newName);

    // The map is keyed by name — must re-insert under the new key, not just mutate.
    FileSystemEntry entry = parent.removeChild(oldName);
    entry.setName(newName);
    parent.addChild(entry);
}
```

> **Senior callout:** *"You can't just call `entry.setName(new)` — the parent's `Map<String, FSEntry>` is keyed by the OLD name. You'd have a ghost entry under the old key and the new name would never resolve. Remove-rename-readd is the only correct sequence."*

### 4.5 Folder's bidirectional consistency (the subtle but critical bit)

```java
public boolean addChild(FileSystemEntry entry) {
    if (entry == null || children.containsKey(entry.getName())) return false;
    children.put(entry.getName(), entry);
    entry.setParent(this);                     // <-- forward and back pointer in sync
    return true;
}

public FileSystemEntry removeChild(String name) {
    FileSystemEntry entry = children.remove(name);
    if (entry != null) entry.setParent(null);  // <-- detach back-pointer too
    return entry;
}
```

> **Senior callout:** *"`addChild` and `removeChild` are the ONLY places allowed to mutate `entry.parent`. If callers could bypass them and set parent directly, the tree would desync — `getPath()` would walk to a parent that doesn't list the child. Tight invariants here are why I made `children` private and exposed methods."*

### 4.6 Verification — dry-run a build + move + rename

```
Setup: empty FileSystem; root = Folder("/"), root.parent = null.

createFolder("/home"):
   resolveParent("/home") -> root      (lastSlash = 0 -> parentPath = "/")
   extractName -> "home"
   root.hasChild("home") -> false
   create Folder("home"), root.addChild(home)
   STATE: root.children = {"home"->Folder}, home.parent = root                       ✓

createFolder("/home/user"):
   resolveParent("/home/user"):
       parentPath = "/home"; resolvePath("/home") -> walks root->"home"; returns home
   extractName -> "user"
   home.hasChild("user") -> false
   create Folder("user"), home.addChild(user)
   STATE: home.children = {"user"->Folder}, user.parent = home                       ✓

createFile("/home/user/notes.txt", "hello world"):
   resolveParent -> user
   extractName -> "notes.txt"
   user.hasChild("notes.txt") -> false
   create File, user.addChild(file)
   STATE: user.children = {"notes.txt"->File}, file.parent = user                    ✓

get("/home/user/notes.txt").getPath():
   notes.parent=user -> user.getPath():
     user.parent=home -> home.getPath():
       home.parent=root -> root.getPath() = "/"
     -> "/" + "home" = "/home"
   -> "/home" + "/" + "user" = "/home/user"
 -> "/home/user" + "/" + "notes.txt" = "/home/user/notes.txt"                         ✓

move("/home/user/notes.txt", "/home/notes.txt"):
   entry = file (under user)
   destParent = home, destName = "notes.txt"
   Cycle check: file is not a directory -> skip
   home.hasChild("notes.txt") -> false
   user.removeChild("notes.txt") -> file (file.parent = null transiently)
   file.setName("notes.txt") -> unchanged
   home.addChild(file) -> file.parent = home
   STATE: user.children = {}, home.children = {"user"->..., "notes.txt"->file}      ✓

rename("/home", "users"):
   parent = root, oldName = "home"
   root.hasChild("home") -> true, root.hasChild("users") -> false
   entry = root.removeChild("home") -> home folder (parent transiently null)
   home.setName("users")
   root.addChild(home) -> home.parent = root (now keyed by "users")
   STATE: root.children = {"users"->home}, home.name = "users"                      ✓

get("/users/notes.txt").getPath():  -- works because getPath walks parent pointers,
                                       which root/home/file all still maintain
   -> "/users/notes.txt"                                                              ✓

move("/users", "/users/user/loop"):   -- attempted cycle
   entry = home folder (now "users"), entry.isDirectory() = true
   destParent = user folder
   Cycle check: cursor = user; user.parent = home/users = entry -> CYCLE DETECTED
   throws InvalidPathException                                                        ✓
```

> **Senior callout from the trace:** *"The trick that makes rename work cheaply: the path is **computed from parent pointers**, never stored. When I rename `/home` to `/users`, I don't touch any descendant — they all auto-resolve to the new path the next time `getPath()` is called. The opposite design (storing the full path string on every entry) would require walking every descendant on every rename — that's O(N) per rename, with N = total descendants."*

---

## STEP 5 — Extensibility (~7 min)

### 5.1 "Make the file system thread-safe"

> **Problem in current design:** *"Right now everything assumes single-threaded access. Two threads both calling `createFile` on the same path can both pass the `hasChild` check, both succeed in `addChild` — and either overwrite silently or corrupt the map. Classic check-then-act race."*
>
> **Pattern as the fix (3-tier menu):**
> 1. **Coarse-grained — method-level `synchronized` on `FileSystem`:** correct, simple, but two threads working in disjoint subtrees block each other.
> 2. **Fine-grained — per-folder lock:** lock just the parent folder being modified. Better throughput in disjoint subtrees, but `move` touches TWO folders → deadlock risk.
> 3. **Deadlock-free move — always acquire locks in path-alphabetical order.** Both threads lock the lower path first, so circular waits are impossible.
>
> **Bonus — read-write lock:** `get`, `list` are read-only → multiple readers can run in parallel. `create`, `delete`, `move`, `rename` are writers → exclusive. For read-heavy workloads (typical for filesystems) this is a 5–10x throughput win.

```java
// Sketch — fine-grained move with lock ordering
public void move(String srcPath, String destPath) {
    Folder srcParent  = resolveParent(srcPath);
    Folder destParent = resolveParent(destPath);

    Folder first  = srcParent.getPath().compareTo(destParent.getPath()) <= 0 ? srcParent : destParent;
    Folder second = (first == srcParent) ? destParent : srcParent;

    synchronized (first) {
        synchronized (second) {
            // ... existing cycle check + remove/setName/add ...
        }
    }
}
```

### 5.2 "Add search by name"

> **Problem in current design:** *"To find a file by name, I'd walk the entire tree — O(N) per search. Tens of thousands of entries means tens of thousands of comparisons every time."*
>
> **Pattern as the fix:** *"Maintain a secondary index — `Map<String, List<FileSystemEntry>> nameIndex` on `FileSystem`. Update it inside `createFile`, `createFolder`, `delete`, `rename`. `search(name)` becomes O(1) by name."*
>
> **Tradeoff:** *"Space — one extra map entry per file/folder. Mutation cost — `rename` now does 2 index updates instead of 0. Both are fine at our scale. For prefix search (`config*`), the map becomes a Trie. For content search, you'd need an inverted index — that's a real search engine, out of scope."*

```java
private final Map<String, List<FileSystemEntry>> nameIndex = new HashMap<>();

// In createFile / createFolder after addChild:
nameIndex.computeIfAbsent(name, k -> new ArrayList<>()).add(entry);

// In delete / rename:
nameIndex.getOrDefault(oldName, List.of()).remove(entry);
```

### 5.3 Other "what-if" answers

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Symbolic links"                           | New `SymLink extends FileSystemEntry` (Composite already supports it). `resolvePath` follows links with a max-depth guard to prevent loops. |
| "Permissions / ownership"                  | Add `owner` + `permissions` fields on `FileSystemEntry`. Operations check them at the public API boundary. |
| "Timestamps (created / modified / accessed)" | Add `createdAt` / `modifiedAt` fields; mutate on every operation. Inject `Clock` for testability (same trick as Parking Lot / Locker). |
| "Disable deleting non-empty folders"       | In `delete`: if `entry.isDirectory() && !entry.getChildren().isEmpty()` → throw. Common Unix `rmdir` semantics. |
| "Relative paths (./ ../)"                  | Add a "current working directory" on `FileSystem`. `resolvePath` joins relative paths against cwd before walking. |
| "Persistence"                              | Inject a `FileSystemRepository`; write on every mutation, replay on boot.                          |

---

## Design Patterns — Hello Interview's canonical 8 + the one extra

The single biggest pattern mistake at SDE‑2 level isn't *not knowing* patterns — it's **forcing them into the wrong step**. Patterns volunteered in Step 1, 2, or 3 sound rehearsed; the same patterns named in Step 5 sound senior — *unless* the pattern is structurally baked into the problem itself, in which case Step 2 / Step 3 is the right place. File System is exactly that case.

> **Hello Interview's stance:** *"Patterns arise from good design decisions, not the other way around. Most interview designs use zero to two patterns maximum."*
>
> **Geography note:** India-based interviews expect candidates to name patterns when applied. Err on the side of naming when it fits.

### The 5-step timing rule (universal — applies to every LLD problem)

| Step                       | Use a pattern here?                                                                 |
| -------------------------- | ----------------------------------------------------------------------------------- |
| **1. Requirements**        | **Never.**                                                                          |
| **2. Entities**            | **Sometimes** — if a clear pattern is baked into the problem domain (Composite for File System; Strategy for Elevator dispatch). |
| **3. Class Design**        | **YES, when you can state the design pressure in one sentence.** Otherwise hardcode and refactor in Step 5. |
| **4. Implementation**      | **No new patterns.**                                                                |
| **5. Extensibility**       | **YES — for additional patterns triggered by follow-up prompts.**                   |

> **File System specifically:** the one pattern in the base is **Composite** — *and that's not even in Hello Interview's canonical 8*. It's there because File and Folder ARE the leaf/composite pair the pattern describes. Naming it is honest, not over-engineering.

### Hello Interview's canonical 8 × interviewer trigger

| # | Pattern              | Category   | Trigger phrase                                                                | One-line response                                                                                       |
| - | -------------------- | ---------- | ------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| 1 | **Strategy** ⭐       | Behavioral | "different rules" · "variants" · "swap at runtime"                              | *"Promote X to an interface; inject the concrete implementation."*                                       |
| 2 | **Observer**         | Behavioral | "notify multiple" · "broadcast" · "event"                                       | *"X publishes events; subscribers register independently."*                                              |
| 3 | **State Machine**    | Behavioral | "behavior depends on state"                                                     | *"Each state is its own class with its own transitions."*                                                |
| 4 | **Factory** (Method) | Creational | "support different types" · "multiple variants"                                 | *"Centralize creation behind a method."*                                                                  |
| 5 | **Builder**          | Creational | "many optional fields" · "complicated construction"                             | *"Builder collects fields incrementally; `build()` validates."*                                          |
| 6 | **Singleton**        | Creational | "exactly one" · "global"                                                         | *"I'd resist textbook Singleton — DI a single instance instead."*                                         |
| 7 | **Decorator**        | Structural | "optional features" · "stack behaviors"                                          | *"Wrap X in decorators, each adding one concern."*                                                       |
| 8 | **Facade**           | Structural | "hide complexity" · "single entry point"                                         | *"Orchestrators usually ARE facades."*                                                                    |
| + | **Composite**        | Structural | "tree" · "uniform treatment of leaves and containers"                            | *"Leaf and composite both implement the same interface so callers treat them uniformly."*                |

### Three rules to sound natural

1. **Cap at 2 patterns total** in one interview.
2. **Always name the concrete win in the same breath.** *"Composite here because callers shouldn't care whether they're looking at a file or a folder — both are tree nodes with the same identity."*
3. **Never volunteer a pattern without a trigger.**

### How this maps to File System specifically

**Already in the BASE design — name these:**

- **Composite (not in HI's 8)** — `FileSystemEntry` is the component; `File` is the leaf; `Folder` is the composite. **Name it once in Step 2.**
- **Facade (#8)** — `FileSystem` is the facade over the tree + path parser + exception machinery. Name it once in Step 2.
- **Information Expert** (GRASP) — `getPath` on FSEntry (only it knows its parent); `addChild` on Folder (only it owns children); path parsing on FileSystem (only it knows about paths).
- **Tell, Don't Ask** — `FileSystem` calls `entry.isDirectory()` rather than `instanceof Folder`.
- **Immutability** — `FileSystemException` and its subclasses are immutable.

**Reach for these on the matching Step-5 follow-up — use 3-beat phrasing (problem → pattern → tradeoff):**

| Follow-up                                  | Pattern                      | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Add symbolic links"                       | **Composite (extension)**    | *"Currently `FileSystemEntry` has two subclasses. To support symlinks, add a third — `SymLink extends FSEntry` — which overrides `getPath` to follow the target. `resolvePath` gains a max-depth guard."* |
| "Notify when files change"                 | **Observer (#2)**            | *"FileSystem publishes `EntryCreated` / `EntryDeleted` / `EntryRenamed` events; loggers, sync engines, dashboards subscribe independently."* |
| "Different search algorithms (exact, prefix, glob)" | **Strategy (#1)** ⭐ | *"Currently no search. When we add it, `SearchStrategy` interface — `ExactSearch` uses a HashMap, `PrefixSearch` uses a Trie, `GlobSearch` walks with regex matching at each node."* |
| "Persist to disk"                          | (Repository — not in HI's 8) | Describe technique: *"Inject a storage interface; FileSystem writes on every mutation, reads on boot. In-memory impl for tests."* |
| "Permissions / ACLs"                       | **Decorator (#7)** option    | *"Wrap each operation in an `AccessCheckingFileSystem` decorator that consults a permission service first."* |

**Patterns to actively refuse:**

- **Singleton on `FileSystem`** — kills tests, blocks multi-FS scenarios (mounting, sandboxes).
- **State pattern on `isDirectory`** — it's a static type discriminator, not behavior that varies over time.
- **Builder for empty `FileSystem()` constructor** — academic noise.
- **Factory for `File` / `Folder`** — direct constructors are clearer.
- **Visitor over the tree** — *tempting*, but only earns rent if you have many distinct operations across the tree. For our 7 operations, plain recursion is simpler.

### One sentence to say at the end of Step 3

> *"The base design names two patterns out loud: Composite (file + folder share a common entry abstraction) and Facade (FileSystem hides path parsing and tree manipulation behind one API). Plus Information Expert as the GRASP principle. No Strategy yet — the requirements don't mandate multiple algorithms for any operation; if Step 5 brings search or thread safety, those will earn their own pattern names."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

Let `D` = depth of the path being resolved, `C` = children at a folder, `N` = total entries in the tree.

| Operation              | Time                                                       | Space                | Notes                                                                              |
| ---------------------- | ---------------------------------------------------------- | -------------------- | ---------------------------------------------------------------------------------- |
| `createFile / createFolder` | **`O(D)`** (path walk) + `O(1)` map insert            | O(1) per call         | D = depth of parent path                                                           |
| `delete(path)`         | **`O(D)`** path walk + `O(1)` map remove                   | O(1) per call         | Note: whole subtree is GC'd in one step; no recursive walk needed                  |
| `list(path)`           | **`O(D + C)`** — walk + copy children                      | O(C) for the copy     | Copy is to defend against caller mutation                                          |
| `get(path)`            | **`O(D)`**                                                 | O(1)                  |                                                                                    |
| `rename(path, new)`    | **`O(D)`** + `O(1)` re-insert                              | O(1) per call         | Path-of-descendants auto-updates because getPath walks parent pointers             |
| `move(src, dest)`      | **`O(D_src + D_dest + D_ancestors)`** — last term is the cycle check | O(1) per call | Cycle check walks at most depth of destParent → root                            |
| `getPath()`            | **`O(D)`** for each call (lazy, walks pointers up to root) | O(D) for the StringBuilder | Could be cached, but cache invalidation on rename/move is the harder problem  |
| Storage — entries      | —                                                          | **`O(N)`**            | One Folder/File per entry; each Folder owns a Map<String, FSEntry>                |

> **Senior callout:** *"All path-touching operations are O(D), where D = depth, not N. For tens of thousands of files but a depth of ~20 nested folders, that's 20 lookups per call. If `O(D)` became a bottleneck (e.g., a recursive backup tool calling `get` once per file), I'd cache resolved paths in an LRU — but cache invalidation on `move`/`rename` is non-trivial, so I'd profile first."*

### 2. Concurrency / thread-safety

Already covered as Step 5.1 — the three-tier menu (coarse → fine-grained → R/W lock) plus the lock-ordering fix for `move` deadlocks. Have these one-liners ready:

| Approach            | When to use                              | Cost                                       |
| ------------------- | ---------------------------------------- | ------------------------------------------ |
| `synchronized(this)` on FileSystem | Default — correct + simple        | Single-threaded throughput                |
| Per-folder lock     | High concurrency in disjoint subtrees    | `move` needs lock ordering or you deadlock |
| ReadWriteLock       | Read-heavy (typical filesystem workload) | Slight write-side cost                    |

### 3. Testing — what to write tests for

The whole design is testable because there's no I/O — every operation is pure tree manipulation.

| Test category               | Cases to cover                                                                                              |
| --------------------------- | ----------------------------------------------------------------------------------------------------------- |
| CRUD happy path             | createFolder + createFile + get + list + delete + rename + move all happen-path                            |
| Path errors                 | null / empty / not-starting-with-slash / consecutive slashes / hitting a file when more components remain  |
| Existence errors            | Create when exists; delete when missing; get when missing                                                   |
| Root protection             | Cannot delete `/`; cannot rename `/`; cannot move `/`; cannot create file at `/`                            |
| Rename consistency          | Renaming a folder updates all descendants' `getPath()` results — even though no descendant fields changed   |
| Move — cycle detection      | Move `/a` into `/a/b/c` → InvalidPathException; move `/a/b` into `/x` (no cycle) → succeeds                 |
| Move — name collision       | Move `/a/file` into `/b` when `/b/file` exists → AlreadyExistsException                                     |
| Bidirectional consistency   | After move, both source-parent.getChildren and dest-parent.getChildren are correct AND entry.parent updated |

```java
@Test
void renaming_folder_updates_descendant_paths_without_touching_them() {
    FileSystem fs = new FileSystem();
    fs.createFolder("/home");
    fs.createFolder("/home/user");
    File f = fs.createFile("/home/user/notes.txt", "x");
    assertEquals("/home/user/notes.txt", f.getPath());

    fs.rename("/home", "users");
    // Same file object — no descendant was touched — but getPath() recomputes:
    assertEquals("/users/user/notes.txt", f.getPath());
}

@Test
void move_into_own_descendant_throws_cycle_error() {
    FileSystem fs = new FileSystem();
    fs.createFolder("/a");
    fs.createFolder("/a/b");
    fs.createFolder("/a/b/c");
    assertThrows(InvalidPathException.class, () -> fs.move("/a", "/a/b/c/loop"));
}

@Test
void distinct_exception_types_for_distinct_failures() {
    FileSystem fs = new FileSystem();
    assertThrows(InvalidPathException.class, () -> fs.createFile("not-absolute", "x"));
    assertThrows(NotFoundException.class,    () -> fs.get("/missing"));
    fs.createFile("/dup.txt", "x");
    assertThrows(AlreadyExistsException.class, () -> fs.createFile("/dup.txt", "x"));
}
```

> **Senior callout:** *"Each guard clause in the public API gets its own test — three distinct exception types make this trivial. If I'd lumped everything into one `FileSystemException` the tests would have to inspect the message string, which is brittle."*

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | FileSystem = path-parsing + orchestration. FSEntry = node identity. Folder = containment. File = content. Four reasons to change → four classes. |
| **O** Open/Closed            | `FileSystemEntry` is extensible — adding `SymLink` doesn't touch `File`, `Folder`, or any existing code. New exception types slot in without breaking callers. |
| **L** Liskov Substitution    | Anywhere I have a `FileSystemEntry` reference, swapping `File ↔ Folder ↔ SymLink` honors the same contract — they all answer `getName`, `getPath`, `isDirectory`. |
| **I** Interface Segregation  | Folder doesn't expose its `Map<String, FSEntry>` directly — 5 focused methods (`addChild`, `removeChild`, `getChild`, `hasChild`, `getChildren`) instead of one fat `getMap()`. |
| **D** Dependency Inversion   | Caller depends on `FileSystem` (the API), not on `Folder` or `File` directly. If `FileSystemRepository` lands later, FileSystem depends on the interface, not concrete persistence. |

### 5. "Summarize your design in 30 seconds"

> *"Four classes: FileSystem, FileSystemEntry (abstract), File, Folder. FileSystem is the facade — it owns root, parses paths, and exposes the 7 public operations. FileSystemEntry is the Composite component — both File and Folder share name + parent + getPath(). File is the leaf with content; Folder is the composite with a `Map<String, FSEntry>` of children. Bidirectional consistency lives in `addChild` and `removeChild` — they're the only places that mutate `parent`. The architectural choice that earns rent is parent pointers over stored paths: rename a folder, and every descendant's `getPath()` auto-resolves to the new path because it walks parents up to root. Move includes a cycle check that walks from dest-parent upward — if we ever hit the entry being moved, we'd create an impossible loop. Path parsing is three private helpers — `resolvePath`, `resolveParent`, `extractName` — that every public method reuses, keeping the public API focused. Extensions: thread-safety via a 3-tier menu (coarse-sync → per-folder lock with ordering → read-write lock); search via a name-index Map updated alongside the tree."*

That's ~50 seconds. Hits: structure (4 classes), the Composite pattern named, the parent-pointer architectural call, the cycle-check insight, the DRY path helpers, and extensibility.

---

## Closing soundbites (memorize these)

- **Opening:** *"File systems are familiar — let me clarify scope before assuming."*
- **Why FileSystemEntry is abstract:** *"Both File and Folder are tree nodes. They share name, parent, and getPath. Without the abstraction, the children map type would be `Object` and we'd cast everywhere."*
- **Why Composite:** *"This is naturally Composite — leaf and composite both implement the same FSEntry contract. Callers treat them uniformly."*
- **Why parent pointers, not stored paths:** *"Renames are O(1) — descendants' paths recompute via parent walks. Stored paths would force me to rewrite every descendant string on every rename — O(N) per rename, bug-prone."*
- **The cycle check:** *"Walk up from dest-parent; if I hit the entry being moved, the move would create a loop. The leetcode-y trap most candidates miss."*
- **The rename dance:** *"You can't just `setName()` — the map's keyed by name. Remove from old key, set name, re-add. Same dance is reused in `move`."*
- **Path helpers:** *"`resolvePath` + `resolveParent` + `extractName` keep the public methods focused. Without them, every method would have a copy of the same path-parsing logic."*
- **On thread-safety:** *"Three-tier menu: coarse sync → per-folder locking with path-order → R/W lock for read-heavy workloads."*

---

## Top mistakes that lose points

- **Skipping the FileSystemEntry abstraction** — `Map<String, Object>` and casts everywhere. Senior interviewers stop you and ask "what do File and Folder share?" If you can't extract it without a hint, that's the mid-level signal.
- **Storing full path strings on each entry** — feels intuitive, but every rename becomes O(N). The interviewer will ask "what happens when I rename a folder with thousands of descendants?" and you'll have to backtrack.
- **Forgetting the cycle check in `move`** — the most common slip. Many candidates pass `move` until the interviewer says "what if I do `move('/home', '/home/user')`?"
- **Calling `entry.setName(new)` without remove/add** — entry orphaned under the old map key. Subtle bug; only surfaces on the next `get`/`list`.
- **Mutating `entry.parent` from outside `addChild`/`removeChild`** — desyncs the bidirectional link. Tight encapsulation is the senior signal here.
- **Lumping all errors into one `Exception`** — the article specifies "specific exception types so callers can handle different failure modes". Use 4 distinct classes.
- **Inlining path parsing in every public method** — DRY violation; the helpers exist for a reason.
- **Adding `Path` as a class** — it has no state and no rules, just parse the string.
- **Pattern-stuffing — adding Strategy / Factory / Builder unprompted** — File System needs Composite + Facade, not the full GoF zoo.
- **Skipping the dry run** — the cycle check + rename dance are exactly where dry-runs catch bugs before the interviewer does.

---

## Files in this folder (your reference implementation)

| File                                       | What it shows                                                                            |
| ------------------------------------------ | ---------------------------------------------------------------------------------------- |
| `model/FileSystemEntry.java`               | Abstract Composite base — name, parent, getPath() walks parent pointers                  |
| `model/File.java`                          | Leaf node — content + isDirectory() = false                                              |
| `model/Folder.java`                        | Composite node — children map; addChild/removeChild keep parent pointers in sync         |
| `exception/FileSystemException.java`       | Base for all FS exceptions (one catch handles all)                                       |
| `exception/InvalidPathException.java`      | null / empty / non-absolute / consecutive slashes                                        |
| `exception/NotFoundException.java`         | Path resolved but entry missing                                                          |
| `exception/AlreadyExistsException.java`    | Create / move / rename into existing name                                                |
| `exception/NotADirectoryException.java`    | Tried to list a file, or pathwalk hit a file when more components remain                 |
| `FileSystem.java`                          | Orchestrator + facade — 7 public methods + 3 private path helpers + cycle check on move  |
| `FileSystemDriver.java`                    | Scenario harness — build/move/rename/cycle/delete-root/delete-subtree                    |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.filesystem.FileSystemDriver
```
