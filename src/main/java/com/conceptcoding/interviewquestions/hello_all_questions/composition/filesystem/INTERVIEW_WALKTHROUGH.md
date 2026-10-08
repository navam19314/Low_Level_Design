# In-Memory File System

> **Why it's in the deck:** **the** Composite-pattern question. Files and folders form a tree, and callers treat a single file and a whole folder the same way (`getSize()`, `getPath()`, `delete`). Also a test of careful path parsing.
>
> **The crux (what's really being tested):**
> 1. **Composite:** `File` (leaf) and `Folder` (container) share one base type; a folder's size = the sum of its children, recursively.
> 2. **Path handling:** absolute paths parsed one component at a time, with every bad input rejected clearly.
> 3. **Tree integrity:** parent pointers kept in sync, so `getPath()` works and a move can't create a loop.
>
> **Family:** composition (Composite pattern). See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md). Compare [Coffee Machine](../coffeemachine/INTERVIEW_WALKTHROUGH.md): Decorator *wraps one* object; Composite *contains many*.

---

## 1. Plain-language picture

### The tree
```
/                         (Folder)
├── readme.txt            (File, 14 bytes)
└── home/                 (Folder)
    └── user/             (Folder)
        ├── notes.txt     (File, 11 bytes)
        └── todo.txt      (File, 8 bytes)
```

### Composite: ask a folder, it asks its children
"How big is `/home/user`?" The folder doesn't know; it asks each child and adds the answers: 11 + 8 = 19. "How big is `/`?" It asks `readme.txt` (14) and `home` (which asks `user`, which answers 19) → 33. The caller never checks "is this a file or a folder?". Both answer `getSize()`, each in its own way.

### Paths are walked one step at a time
`/home/user/notes.txt` → start at root → child "home" → child "user" → child "notes.txt". If any step is missing, it's "not found"; if a step is a file but more steps remain, it's "not a directory".

### Each node knows its parent
`notes.txt` doesn't store "/home/user/notes.txt". It stores its name and its parent, and builds the path by walking up. Rename `home` to `house` and every path under it changes automatically, with nothing to update.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | Abstract `FileSystemEntry` + `File` (leaf) + `Folder` (composite) | one `Node` class with an `isFile` flag | Each type implements `getSize()`/`isDirectory()` itself; no `if (isFile)` anywhere. |
| D2 | `Folder` keeps `LinkedHashMap<name, entry>` | a `List` of children | O(1) lookup by name; insertion order for stable listings; duplicate names caught immediately. |
| D3 | Parent pointer on each entry; path **computed** by walking up | storing the full path string | A rename or move can't leave stale paths behind. |
| D4 | `addChild`/`removeChild` also set/clear the parent pointer | callers setting parents themselves | Both directions always agree. |
| D5 | One `validatePath()` at the start of every public method | checking ad hoc | Trailing slashes, `//`, relative paths, and empty names are all rejected the same way. |
| D6 | One `FileSystemException` with clear messages | five exception subclasses | Callers catch one type; subclasses only if someone needs to branch on the kind. |
| D7 | Root is special: can't be created, deleted, renamed or moved | treating root like any folder | Prevents a tree with no root. |
| D8 | Single-threaded by contract in the base | locks everywhere | Say it; the concurrency upgrade is a follow-up (Q5). |

### Class shape
```
FileSystem                          ← createFile · createFolder · delete · list · get
  Folder root · validatePath · resolvePath (walk) · resolveParent · extractName

«abstract» FileSystemEntry { name, parent }   getPath() (walk up) · isDirectory() · getSize()
  ├── File   { content }            getSize() = content length              (leaf)
  └── Folder { LinkedHashMap children }  getSize() = Σ children.getSize()   (composite)
                                    addChild · removeChild · getChild · hasChild · getChildren
FileSystemException
```

---

## 3. Patterns that earn their place

| Pattern | Problem it solves | Without it |
|---|---|---|
| **Composite** | treat a file and a folder of folders the same way (`getSize`, `getPath`, `delete`) | `if (entry instanceof Folder) { loop... } else { ... }` in every operation |

**Say:** *"Composite: File is the leaf, Folder is the composite, both extend FileSystemEntry. A folder's size is the sum of its children's, recursively, and callers never branch on the type."*

**Tempting but wrong:** a Singleton file system (tests need fresh trees), a Visitor in the base (only worth it when many different operations walk the tree: Q4), a Builder for paths.

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** An in-memory tree of folders and files with absolute paths like `/a/b/c.txt`. Create file/folder, delete, list, read?
> **Interviewer:** Yes.
> **You:** Does creating `/a/b/c.txt` auto-create `/a/b`, or must parents exist?
> **Interviewer:** Parents must exist.
> **You:** Delete a folder → its whole subtree?
> **Interviewer:** Yes.
> **You:** Folder size = sum of contents?
> **Interviewer:** Yes, good.
> **You:** Move, rename, search, permissions: later?
> **Interviewer:** Later.

```
In scope:  createFile(path, content) · createFolder(path) · delete(path) · list(path) · get(path)
           getSize (recursive) · getPath (from parent pointers) · strict path validation
Out:       move/rename, search, permissions, symlinks, concurrency
```

### Timeline
| Min | Do |
|---|---|
| 5–9 | Draw the tree. Say "Composite" and explain `getSize`. |
| 9–17 | Code step 1: `FileSystemEntry` (+ `getPath`), `File`, `Folder` (+ `addChild`/`removeChild`/`getSize`). |
| 17–25 | Code step 2: path helpers: `validatePath`, `resolvePath`, `resolveParent`, `extractName`. |
| 25–33 | Code step 3: `createFile`, `createFolder`, `delete`, `list`, `get`. |
| 33–37 | Dry run: sizes; a bad path; deleting a subtree. |
| 37–45 | Follow-ups: move (the cycle check), rename, find. |

### The code you write, in this order

**Step 1: the Composite** ([model/](model/))
```java
public abstract class FileSystemEntry {
    private String name;
    private Folder parent;

    protected FileSystemEntry(String name) { this.name = name; }

    public String getPath() {                              // walk up; never stored
        if (parent == null) return name;                   // root's name is "/"
        String parentPath = parent.getPath();
        return parentPath.equals("/") ? "/" + name : parentPath + "/" + name;
    }

    public abstract boolean isDirectory();
    public abstract long getSize();                        // the Composite operation
    // + getName, setName, getParent, setParent
}

public class File extends FileSystemEntry {
    private String content;
    public File(String name, String content) { super(name); this.content = content; }
    public boolean isDirectory() { return false; }
    public long getSize() { return content == null ? 0 : content.length(); }      // leaf: itself
    // + getContent, setContent
}

public class Folder extends FileSystemEntry {
    private final Map<String, FileSystemEntry> children = new LinkedHashMap<>();
    public Folder(String name) { super(name); }

    public boolean isDirectory() { return true; }

    public long getSize() {                                // composite: ask the children
        long total = 0;
        for (FileSystemEntry child : children.values()) total += child.getSize();
        return total;
    }

    public boolean addChild(FileSystemEntry entry) {       // keeps the parent pointer in sync
        if (entry == null || children.containsKey(entry.getName())) return false;
        children.put(entry.getName(), entry);
        entry.setParent(this);
        return true;
    }

    public FileSystemEntry removeChild(String name) {
        FileSystemEntry entry = children.remove(name);
        if (entry != null) entry.setParent(null);          // detached: no stale back-pointer
        return entry;
    }

    public FileSystemEntry getChild(String name) { return children.get(name); }
    public boolean hasChild(String name)         { return children.containsKey(name); }
    public List<FileSystemEntry> getChildren()   { return new ArrayList<>(children.values()); }
}
```

**Step 2: paths** ([FileSystem.java](FileSystem.java))
```java
private static void validatePath(String path) {          // every public method calls this first
    if (path == null || path.isEmpty()) throw new FileSystemException("Path cannot be null or empty");
    if (!path.startsWith("/"))           throw new FileSystemException("Path must be absolute: " + path);
    if (path.equals("/")) return;
    if (path.contains("//"))             throw new FileSystemException("Invalid path (consecutive slashes): " + path);
    if (path.endsWith("/"))              throw new FileSystemException("Path must not end with '/': " + path);
}

private FileSystemEntry resolvePath(String path) {        // walk from the root, one name at a time
    validatePath(path);
    if (path.equals("/")) return root;
    FileSystemEntry current = root;
    for (String part : path.substring(1).split("/")) {
        if (!current.isDirectory()) throw new FileSystemException("Not a directory: " + current.getPath());
        FileSystemEntry child = ((Folder) current).getChild(part);
        if (child == null) throw new FileSystemException("Path not found: " + path);
        current = child;
    }
    return current;
}

private Folder resolveParent(String path) {
    int lastSlash = path.lastIndexOf('/');
    String parentPath = (lastSlash == 0) ? "/" : path.substring(0, lastSlash);
    FileSystemEntry parent = resolvePath(parentPath);
    if (!parent.isDirectory()) throw new FileSystemException("Parent is not a directory: " + parentPath);
    return (Folder) parent;
}

private String extractName(String path) {
    String name = path.substring(path.lastIndexOf('/') + 1);
    if (name.isEmpty()) throw new FileSystemException("Path must end with a name: " + path);
    return name;
}
```

**Step 3: the operations**
```java
public File createFile(String path, String content) {
    validatePath(path);
    if (path.equals("/")) throw new FileSystemException("Cannot create file at root");
    String name = extractName(path);
    Folder parent = resolveParent(path);
    if (parent.hasChild(name)) throw new FileSystemException("Entry already exists: " + path);
    File file = new File(name, content);
    parent.addChild(file);
    return file;
}
// createFolder: identical with new Folder(name)

public void delete(String path) {
    validatePath(path);
    if (path.equals("/")) throw new FileSystemException("Cannot delete root");
    if (resolveParent(path).removeChild(extractName(path)) == null) {
        throw new FileSystemException("Entry not found: " + path);
    }                                                      // a folder takes its whole subtree with it
}

public List<FileSystemEntry> list(String path) {
    FileSystemEntry entry = resolvePath(path);
    if (!entry.isDirectory()) throw new FileSystemException("Cannot list a file: " + path);
    return ((Folder) entry).getChildren();
}

public FileSystemEntry get(String path) { return resolvePath(path); }
```

### Dry run
```
createFolder /home → root.addChild(home)      createFolder /home/user
createFile /home/user/notes.txt "hello world" (11)   createFile /home/user/todo.txt "buy milk" (8)
createFile /readme.txt "top-level file" (14)
get("/").getSize() → readme 14 + home.getSize() → user.getSize() → 11 + 8 = 19 → 33
createFile "/home/" → "Path must not end with '/'"      createFile "relative.txt" → "Path must be absolute"
delete /home → root.removeChild("home") → the whole subtree is unreachable → get("/home") → not found
```

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Move a file or folder (the important one: a folder can't move into its own subfolder).</b></summary>

Walk **up** from the destination; if you meet the entry being moved, the destination is inside it, so refuse:
```java
public void move(String srcPath, String destFolderPath) {
    FileSystemEntry entry = get(srcPath);
    if (entry.getParent() == null) throw new FileSystemException("Cannot move root");
    FileSystemEntry dest = get(destFolderPath);
    if (!dest.isDirectory()) throw new FileSystemException("Destination is not a folder: " + destFolderPath);
    for (Folder f = (Folder) dest; f != null; f = f.getParent()) {          // walk UP from the destination
        if (f == entry) throw new FileSystemException("Cannot move " + srcPath + " into itself or its own subfolder");
    }
    Folder target = (Folder) dest;
    if (target.hasChild(entry.getName())) throw new FileSystemException(destFolderPath + " already has " + entry.getName());
    entry.getParent().removeChild(entry.getName());
    target.addChild(entry);                                                 // fixes the parent pointer
}
// move("/docs", "/docs/old") → rejected; otherwise /docs would be its own descendant: a loop, unreachable from root
```
The cycle check is O(depth), thanks to the parent pointers. Every path under the moved folder updates automatically, because paths are computed.
</details>

<details>
<summary><b>Q2. Rename.</b></summary>

The parent's map is **keyed by name**, so remove → rename → re-add (check the new name is free first):
```java
public void rename(String path, String newName) {
    FileSystemEntry entry = get(path);
    Folder parent = entry.getParent();
    if (parent == null) throw new FileSystemException("Cannot rename root");
    if (newName.isEmpty() || newName.contains("/")) throw new FileSystemException("Invalid name: " + newName);
    if (parent.hasChild(newName)) throw new FileSystemException(newName + " already exists");
    parent.removeChild(entry.getName());
    entry.setName(newName);
    parent.addChild(entry);
}
```
Forgetting the map-key step leaves the entry findable only by its **old** name. That's a classic bug interviewers watch for.
</details>

<details>
<summary><b>Q3. Find every `.pdf` under a folder.</b></summary>

Depth-first recursion. This is the Composite again, walking the tree:
```java
static List<String> find(Folder folder, String suffix) {
    List<String> found = new ArrayList<>();
    for (FileSystemEntry child : folder.getChildren()) {
        if (child.isDirectory()) found.addAll(find((Folder) child, suffix));
        else if (child.getName().endsWith(suffix)) found.add(child.getPath());
    }
    return found;
}
```
For "find by size > 1 MB" or "modified today", pass a `Predicate<FileSystemEntry>` instead of a suffix: one method, any filter. On deep trees, use an explicit stack instead of recursion to avoid stack overflow.
</details>

<details>
<summary><b>Q4. Many different operations walk the tree (size, count, search, export). How do you keep File/Folder small?</b></summary>

The **Visitor** pattern: `entry.accept(visitor)`; `Folder.accept` visits itself and then its children. A `SizeVisitor`, a `SearchVisitor` and a `JsonExportVisitor` each live in their own class, so File/Folder don't grow a method per operation. Only worth it when there are several such operations. With just `getSize`, the plain Composite method is simpler; say that.
</details>

<details>
<summary><b>Q5. Make it thread-safe.</b></summary>

Simplest correct: one `ReentrantReadWriteLock` for the whole tree. Reads (`get`, `list`, `getSize`) share the read lock; writes (`create`, `delete`, `move`) take the write lock. A move touches two folders and needs a cycle check across the tree, so a single lock is the safe default. Finer-grained (a lock per folder, taken top-down in path order) is possible, but it's tricky with move; say why you'd avoid it unless measured.
</details>

<details>
<summary><b>Q6. `getSize()` on the root is slow for a huge tree.</b></summary>

Cache the size on each folder and update it **up the parent chain** on every change (add/remove/write: walk up adding the delta). Reads become O(1) and writes O(depth). The parent pointers make this cheap: one more payoff of D3.
</details>

<details>
<summary><b>Q7. Permissions (read/write per user).</b></summary>

Each entry gets an owner and permission bits (`rwx` for owner/others). Check them during `resolvePath` (execute on every folder you pass through) and before the operation (write on the parent to create or delete). If an entry has no explicit permission, inherit from its parent: walk up, again via the parent pointer.
</details>

<details>
<summary><b>Q8. How do you test it?</b></summary>

Create a tree, list, read content, folder sizes roll up (19, 33), every bad path (trailing `/`, `//`, relative, empty), duplicate names, a missing path, listing a file, deleting root, deleting a subtree. All are in the driver. For move: into a sibling works; into itself or a descendant is refused.
</details>

---

## 6. Traps
1. One class with `isFile` flags instead of Composite.
2. Storing full path strings (stale after a rename or move).
3. Rename without re-keying the parent's map.
4. Move without the cycle check.
5. Accepting `/a/`, `a/b`, `/a//b` (nameless or ghost entries; a crash on relative paths).
6. Forgetting to update parent pointers in `addChild`/`removeChild`.

## 7. Recall check
1. What does `getSize()` do in File vs Folder, and why does the caller never check the type?
2. How is `getPath()` computed, and why not store it?
3. Write the move cycle check from memory.
4. Rename: why remove, rename, re-add?
5. Which paths does `validatePath` reject?

**Rebuild in 12 minutes:** `FileSystemEntry` (getPath) · `File` · `Folder` (map, addChild/removeChild, getSize) · `validatePath` · `resolvePath` · `createFile` · `delete`.

---

**Files:** `FileSystem` · `model/` (`FileSystemEntry`, `File`, `Folder`) · `exception/FileSystemException` · `FileSystemDriver` (tree, list, read, sizes roll up, bad paths, duplicates, missing, list a file, delete root, delete subtree)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.composition.filesystem.FileSystemDriver
```
