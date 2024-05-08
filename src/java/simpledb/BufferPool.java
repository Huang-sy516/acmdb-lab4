// package simpledb;

// import java.io.*;
// import java.util.List;
// import java.util.Map;
// import java.util.Vector;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.Iterator;
// /**
//  * BufferPool manages the reading and writing of pages into memory from
//  * disk. Access methods call into it to retrieve pages, and it fetches
//  * pages from the appropriate location.
//  * <p>
//  * The BufferPool is also responsible for locking;  when a transaction fetches
//  * a page, BufferPool checks that the transaction has the appropriate
//  * locks to read/write the page.
//  * 
//  * @Threadsafe, all fields are final
//  */
// public class BufferPool {
//     /** Bytes per page, including header. */
//     private static final int PAGE_SIZE = 4096;

//     private static int pageSize = PAGE_SIZE;
    
//     /** Default number of pages passed to the constructor. This is used by
//     other classes. BufferPool should use the numPages argument to the
//     constructor instead. */
//     public static final int DEFAULT_PAGES = 50;

//     // the maximum number of pages the BufferPool can cache
//     private final int numPages;
//     private final ConcurrentHashMap<PageId, DoublyLinkedNode<Page>> lruCache;  // 防止锁的占用

//     private final DoublyLinkedNode<Page> head;
//     private final DoublyLinkedNode<Page> tail;  // lruCache的头和尾

//     private final LockController  LockController ;

//     /**
//      * Creates a BufferPool that caches up to numPages pages.
//      *
//      * @param numPages maximum number of pages in this buffer pool.
//      */
//     public BufferPool(int numPages) {
//         // some code goes here
//         this.numPages = numPages;
//         this.lruCache = new ConcurrentHashMap<>();
//         this.head = new DoublyLinkedNode<>();
//         this.tail = new DoublyLinkedNode<>();
//         this.head.linkSuccessor(this.tail);
//         this.tail.linkPredecessor(this.head);

//         this.LockController  = new LockController ();
//     }


//     public static class ResourceLock {
//         // Transaction identifier for the lock
//         private TransactionId transactionId;

//         // Permission level of the lock
//         private Permissions lockPermission;

//         // Constructor for ResourceLock
//         public ResourceLock(TransactionId transactionId, Permissions lockPermission) {
//             this.transactionId = transactionId;
//             this.lockPermission = lockPermission;
//         }

//         // Retrieve the permission level of this lock
//         public Permissions getLockPermission() {
//             return lockPermission;
//         }

//         // Retrieve the transaction ID associated with this lock
//         public TransactionId getTransactionId() {
//             return transactionId;
//         }

//         // Set a new transaction ID for this lock
//         public void setTransactionId(TransactionId transactionId) {
//             this.transactionId = transactionId;
//         }

//         // Update the permission level of this lock
//         public void setLockPermission(Permissions lockPermission) {
//             this.lockPermission = lockPermission;
//         }
//     }


    

//     public static class LockController {
//         private final ConcurrentHashMap<PageId, Vector<ResourceLock>> pageLocks;
    
//         public LockController() {
//             pageLocks = new ConcurrentHashMap<>();
//         }
    
//         // Remove a specific lock and potentially the entry if no locks remain
//         public synchronized void releaseLock(TransactionId transactionId, PageId pageId) {
//             Vector<ResourceLock> locks = this.pageLocks.get(pageId);
//             int lockIndex = -1;
//             boolean isLockFound = false;
//             for (int i = 0; i < locks.size(); i++) {
//                 if (locks.get(i).getTransactionId().equals(transactionId)) {
//                     isLockFound = true;
//                     lockIndex = i;
//                     break;
//                 }
//             }
//             if (isLockFound) {
//                 locks.remove(lockIndex);
//                 if (locks.isEmpty()) {
//                     this.pageLocks.remove(pageId);
//                 }
//             }
//         }
    
//         // Remove all locks associated with a specific transaction
//         public synchronized void removeAllLocks(TransactionId transactionId) {
//             Iterator<Map.Entry<PageId, Vector<ResourceLock>>> it = this.pageLocks.entrySet().iterator();
//             while (it.hasNext()) {
//                 Map.Entry<PageId, Vector<ResourceLock>> entry = it.next();
//                 Vector<ResourceLock> locks = entry.getValue();
//                 locks.removeIf(lock -> lock.getTransactionId().equals(transactionId));
//                 if (locks.isEmpty()) {
//                     it.remove();
//                 }
//             }
//         }
    
//         // Attempt to set a lock on a page
//         public synchronized boolean acquireLock(PageId pageId, TransactionId transactionId, Permissions lockType) {
//             Vector<ResourceLock> locks = this.pageLocks.get(pageId);
//             if (locks == null) {
//                 locks = new Vector<>();
//                 locks.add(new ResourceLock(transactionId, lockType));
//                 this.pageLocks.put(pageId, locks);
//                 return true;
//             } else {
//                 return manageLockConflict(locks, transactionId, lockType, pageId);
//             }
//         }
    
//         // Handles lock conflicts and upgrade scenarios
//         private boolean manageLockConflict(Vector<ResourceLock> locks, TransactionId transactionId, Permissions lockType, PageId pageId) {
//             if (lockType.equals(Permissions.READ_WRITE)) {
//                 return manageWriteLock(locks, transactionId, pageId, lockType);
//             } else {
//                 return manageReadLock(locks, transactionId, lockType);
//             }
//         }
    
//         private boolean manageWriteLock(Vector<ResourceLock> locks, TransactionId transactionId, PageId pageId, Permissions lockType) {
//             if (locks.size() == 1) {
//                 ResourceLock firstLock = locks.get(0);
//                 if (firstLock.getTransactionId().equals(transactionId)) {
//                     if (firstLock.getLockPermission().equals(Permissions.READ_ONLY)) {
//                         firstLock.setLockPermission(lockType);
//                     }
//                     return true;
//                 } else {
//                     return false;
//                 }
//             }
//             return false;
//         }
    
//         private boolean manageReadLock(Vector<ResourceLock> locks, TransactionId transactionId, Permissions lockType) {
//             for (ResourceLock lock : locks) {
//                 if (lock.getLockPermission().equals(Permissions.READ_WRITE)) {
//                     return lock.getTransactionId().equals(transactionId) && locks.size() == 1;
//                 }
//                 if (lock.getTransactionId().equals(transactionId)) {
//                     return true;
//                 }
//             }
//             locks.add(new ResourceLock(transactionId, lockType));
//             return true;
//         }
    
//         // Check if a transaction holds any lock on a page
//         public synchronized boolean isLockHeld(PageId pageId, TransactionId transactionId) {
//             Vector<ResourceLock> locks = this.pageLocks.get(pageId);
//             for (ResourceLock lock : locks) {
//                 if (lock.getTransactionId().equals(transactionId)) {
//                     return true;
//                 }
//             }
//             return false;
//         }
    
//     }
    
//     public class DoublyLinkedNode<E> {
//         private E element; // Stores the data for this node
//         private DoublyLinkedNode<E> successor; // Reference to the next node in the list
//         private DoublyLinkedNode<E> predecessor; // Reference to the previous node in the list
    
//         // Default constructor initializing next and previous nodes as null
//         public DoublyLinkedNode() {
//             this.successor = null;
//             this.predecessor = null;
//         }
    
//         // Constructor with data initialization
//         public DoublyLinkedNode(E elementValue) {
//             this.element = elementValue;
//             this.successor = null;
//             this.predecessor = null;
//         }
    
//         // Sets the next node in the list
//         public void linkSuccessor(DoublyLinkedNode<E> nextNode) {
//             this.successor = nextNode;
//         }
    
//         // Sets the previous node in the list
//         public void linkPredecessor(DoublyLinkedNode<E> previousNode) {
//             this.predecessor = previousNode;
//         }
    
//         // Updates the data stored in this node
//         public void updateElement(E newData) {
//             this.element = newData;
//         }
    
//         // Retrieves the data stored in this node
//         public E getElement() {
//             return element;
//         }
    
//         // Retrieves the next node in the list
//         public DoublyLinkedNode<E> getSuccessor() {
//             return successor;
//         }
    
//         // Retrieves the previous node in the list
//         public DoublyLinkedNode<E> getPredecessor() {
//             return predecessor;
//         }
//     }
    
    

//     // Add element into the doublyLinkedNode
//     public void put(DoublyLinkedNode<Page> curNode) {
//         curNode.linkSuccessor(this.head.getSuccessor());
//         curNode.linkPredecessor(this.head);  
//         this.head.getSuccessor().linkPredecessor(curNode);  
//         this.head.linkSuccessor(curNode);
//     }

//     // // Delete element from the doublyLinkedNode
//     public void remove(DoublyLinkedNode<Page> curNode) {
//         DoublyLinkedNode<Page> prevNode = curNode.getPredecessor();
//         DoublyLinkedNode<Page> nextNode = curNode.getSuccessor();
//         prevNode.linkSuccessor(nextNode);
//         nextNode.linkPredecessor(prevNode);
//     }

//     public DoublyLinkedNode<Page> getTail() {
//         return this.tail.getPredecessor();
//     }
    
//     public static int getPageSize() {
//       return pageSize;
//     }
    
//     // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
//     public static void setPageSize(int pageSize) {
//     	BufferPool.pageSize = pageSize;
//     }
    
//     // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
//     public static void resetPageSize() {
//     	BufferPool.pageSize = PAGE_SIZE;
//     }

//     /**
//      * Retrieve the specified page with the associated permissions.
//      * Will acquire a lock and may block if that lock is held by another
//      * transaction.
//      * <p>
//      * The retrieved page should be looked up in the buffer pool.  If it
//      * is present, it should be returned.  If it is not present, it should
//      * be added to the buffer pool and returned.  If there is insufficient
//      * space in the buffer pool, an page should be evicted and the new page
//      * should be added in its place.
//      *
//      * @param tid the ID of the transaction requesting the page
//      * @param pid the ID of the requested page
//      * @param perm the requested permissions on the page
//      */
//     public Page getPage(TransactionId tid, PageId pid, Permissions perm)
//             throws TransactionAbortedException, DbException {
//         // Attempt to acquire a lock on the page with a timeout.
//         ensureLockIsAcquired(tid, pid, perm);

//         // Retrieve or load the page into cache as needed.
//         DoublyLinkedNode<Page> currentPage = retrievePage(pid);

//         // Update the cache to reflect recent access
//         refreshCachePosition(currentPage);

//         // Return the data held by the node, which is the requested page.
//         return currentPage.getElement();
//     }

//     private void ensureLockIsAcquired(TransactionId tid, PageId pid, Permissions perm) throws TransactionAbortedException {
//         long startTime = System.currentTimeMillis();
//         final long timeout = 100;  // Timeout for lock acquisition in milliseconds.
//         boolean lockAcquired = false;

//         while (!lockAcquired) {
//             if (System.currentTimeMillis() - startTime > timeout) {
//                 throw new TransactionAbortedException();
//             }
//             lockAcquired = this.LockController.acquireLock(pid, tid, perm);
//         }
//     }

//     private DoublyLinkedNode<Page> retrievePage(PageId pid) throws DbException {
//         DoublyLinkedNode<Page> pageNode = this.lruCache.get(pid);
//         if (pageNode == null) {
//             pageNode = loadPageIntoCache(pid);
//         }
//         return pageNode;
//     }

//     private DoublyLinkedNode<Page> loadPageIntoCache(PageId pid) throws DbException {
//         if (this.lruCache.size() == this.numPages) {
//             evictPage();
//         }
//         DoublyLinkedNode<Page> newPageNode = new DoublyLinkedNode<>(Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid));
//         this.lruCache.put(pid, newPageNode);
//         this.put(newPageNode);
//         return newPageNode;
//     }

//     private void refreshCachePosition(DoublyLinkedNode<Page> pageNode) {
//         this.remove(pageNode);
//         this.put(pageNode);
//     }



//     // Restores all pages modified by a specific transaction back to their state on disk
//     public synchronized void restorePages(TransactionId tid) {
//         for (DoublyLinkedNode<Page> currentNode : this.lruCache.values()) {
//             // Check if the current page was modified by the transaction
//             if (transactionIsDirty(currentNode, tid)) {
//                 // Revert the page to its original state from disk
//                 restorePageFromDisk(currentNode);
//             }
//         }
//     }

//     // Checks if the transaction is the one that dirtied the page
//     private boolean transactionIsDirty(DoublyLinkedNode<Page> node, TransactionId tid) {
//         TransactionId dirtyTransaction = node.getElement().isDirty();
//         return tid.equals(dirtyTransaction);
//     }

//     // Restores a page from disk and updates the LRU cache
//     private void restorePageFromDisk(DoublyLinkedNode<Page> node) {
//         PageId pageId = node.getElement().getId();
//         // Read the original page from the disk
//         Page originalPage = Database.getCatalog().getDatabaseFile(pageId.getTableId()).readPage(pageId);
//         // Remove the node from the current position in the cache
//         this.remove(node);
//         // Update the node with the original page
//         node.updateElement(originalPage);
//         // Reinsert the node into the cache to update its position
//         this.put(node);
//         // Update the cache map with the restored page
//         this.lruCache.put(originalPage.getId(), node);
//     }

    

//     /**
//      * Releases the lock on a page.
//      * Calling this is very risky, and may result in wrong behavior. Think hard
//      * about who needs to call this and why, and why they can run the risk of
//      * calling it.
//      *
//      * @param tid the ID of the transaction requesting the unlock
//      * @param pid the ID of the page to unlock
//      */
//     public  void releasePage(TransactionId tid, PageId pid) {
//         // some code goes here
//         // not necessary for lab1|lab2
//     }

//     /**
//      * Release all locks associated with a given transaction.
//      *
//      * @param tid the ID of the transaction requesting the unlock
//      */
//     public void transactionComplete(TransactionId tid) throws IOException {
//         // some code goes here
//         // not necessary for lab1|lab2
//     }

//     /** Return true if the specified transaction has a lock on the specified page */
//     public boolean holdsLock(TransactionId tid, PageId p) {
//         // some code goes here
//         // not necessary for lab1|lab2
//         return false;
//     }

//     /**
//      * Commit or abort a given transaction; release all locks associated to
//      * the transaction.
//      *
//      * @param tid the ID of the transaction requesting the unlock
//      * @param commit a flag indicating whether we should commit or abort
//      */
//     public void transactionComplete(TransactionId tid, boolean commit)
//         throws IOException {
//         // some code goes here
//         // not necessary for lab1|lab2
//     }

//     /**
//      * Add a tuple to the specified table on behalf of transaction tid.  Will
//      * acquire a write lock on the page the tuple is added to and any other 
//      * pages that are updated (Lock acquisition is not needed for lab2). 
//      * May block if the lock(s) cannot be acquired.
//      * 
//      * Marks any pages that were dirtied by the operation as dirty by calling
//      * their markDirty bit, and adds versions of any pages that have 
//      * been dirtied to the cache (replacing any existing versions of those pages) so 
//      * that future requests see up-to-date pages. 
//      *
//      * @param tid the transaction adding the tuple
//      * @param tableId the table to add the tuple to
//      * @param t the tuple to add
//      */
//     public void insertTuple(TransactionId tid, int tableId, Tuple tuple)
//         throws DbException, IOException, TransactionAbortedException {
//         // Retrieve the database file associated with the given table ID
//         DbFile databaseFile = Database.getCatalog().getDatabaseFile(tableId);
//         // Insert the tuple into the file and get the affected pages
//         List<Page> affectedPages = databaseFile.insertTuple(tid, tuple);
//         updateCacheWithAffectedPages(tid, affectedPages);
//         // DbFile dbFile = Database.getCatalog().getDatabaseFile(tableId);
//         // List<Page> curPage = dbFile.insertTuple(tid, tuple);
//         // for (Page page : curPage) {
//         //     page.markDirty(true, tid);
//         //     DoublyLinkedNode<Page> curNode = new DoublyLinkedNode<>(page);
//         //     if (this.lruCache.get(page.getId()) != null) {
//         //         // BTree的测试中，page可能会分裂，新分裂出来的page可能不在cache中，自然不用remove
//         //         this.remove(this.lruCache.get(page.getId()));
//         //     }
//         //     this.put(curNode);
//         //     this.lruCache.put(curNode.getElement().getId(), curNode);
//         // }
//     }

//     // Updates the cache with the pages affected by the tuple insertion
//     private void updateCacheWithAffectedPages(TransactionId tid, List<Page> affectedPages) throws IOException {
//         for (Page page : affectedPages) {
//             // Mark the page as dirty since it has been modified
//             page.markDirty(true, tid);
//             // Check if the page is already in cache and remove it if necessary
//             removePageIfPresent(page);
//             // Add the modified page back into the cache
//             addToCache(page);
//         }
//     }

//     // Checks if a page is in the cache and removes it if present
//     private void removePageIfPresent(Page page) {
//         DoublyLinkedNode<Page> existingNode = this.lruCache.get(page.getId());
//         if (existingNode != null) {
//             this.remove(existingNode);
//         }
//     }

//     // Adds a page to the cache, wrapping it in a new doubly linked node
//     private void addToCache(Page page) {
//         DoublyLinkedNode<Page> newNode = new DoublyLinkedNode<>(page);
//         this.put(newNode);
//         this.lruCache.put(page.getId(), newNode);
//     }


//     /**
//      * Remove the specified tuple from the buffer pool.
//      * Will acquire a write lock on the page the tuple is removed from and any
//      * other pages that are updated. May block if the lock(s) cannot be acquired.
//      *
//      * Marks any pages that were dirtied by the operation as dirty by calling
//      * their markDirty bit, and adds versions of any pages that have 
//      * been dirtied to the cache (replacing any existing versions of those pages) so 
//      * that future requests see up-to-date pages. 
//      *
//      * @param tid the transaction deleting the tuple.
//      * @param t the tuple to delete
//      */
//     public void deleteTuple(TransactionId tid, Tuple tuple)
//         throws DbException, IOException, TransactionAbortedException {
//         // Retrieve the table ID from the tuple's record ID
//         int tableId = tuple.getRecordId().getPageId().getTableId();
//         // Retrieve the database file corresponding to the table ID
//         DbFile databaseFile = Database.getCatalog().getDatabaseFile(tableId);
//         // Delete the tuple from the file and get the list of affected pages
//         List<Page> affectedPages = databaseFile.deleteTuple(tid, tuple);
//         // Update the cache with the pages that have been affected by the deletion
//         updateCacheAfterDeletion(tid, affectedPages);
//         // int tableid = tuple.getRecordId().getPageId().getTableId();
//         // DbFile dbFile = Database.getCatalog().getDatabaseFile(tableid);
//         // List<Page> curPage = dbFile.deleteTuple(tid, tuple);
//         // for (Page page : curPage) {
//         //     page.markDirty(true, tid);
//         //     DoublyLinkedNode<Page> curNode = new DoublyLinkedNode<>(page);
//         //     if (this.lruCache.get(page.getId()) != null) {
//         //         this.remove(this.lruCache.get(page.getId()));
//         //     }
//         //     this.put(curNode);
//         //     this.lruCache.put(page.getId(), curNode);
//         // }
//     }

//     // Updates the cache after deleting a tuple, handling any necessary cache removals and additions
//     private void updateCacheAfterDeletion(TransactionId tid, List<Page> affectedPages) throws IOException {
//         for (Page page : affectedPages) {
//             // Mark the page as dirty because it has been modified
//             page.markDirty(true, tid);
//             // Check if the page is already in the cache and remove it if present
//             removePageFromCache(page);
//             // Add the updated page back into the cache
//             addPageToCache(page);
//         }
//     }

//     // Removes a page from the cache if it is present
//     private void removePageFromCache(Page page) {
//         DoublyLinkedNode<Page> existingNode = this.lruCache.get(page.getId());
//         if (existingNode != null) {
//             this.remove(existingNode);
//         }
//     }

//     // Adds a page to the cache and updates the LRU cache accordingly
//     private void addPageToCache(Page page) {
//         DoublyLinkedNode<Page> newNode = new DoublyLinkedNode<>(page);
//         this.put(newNode);
//         this.lruCache.put(page.getId(), newNode);
//     }


//     /**
//      * Flush all dirty pages to disk.
//      * NB: Be careful using this routine -- it writes dirty data to disk so will
//      *     break simpledb if running in NO STEAL mode.
//      */
//     public synchronized void flushAllPages() throws IOException {
//         // some code goes here
//         // not necessary for lab1
//         for (Map.Entry<PageId, DoublyLinkedNode<Page>> entry : this.lruCache.entrySet()) {
//             if (entry.getValue().getElement().isDirty() != null) {
//                 this.flushPage(entry.getValue().getElement().getId());
//             }
//         }

//     }

//     /** Remove the specific page id from the buffer pool.
//         Needed by the recovery manager to ensure that the
//         buffer pool doesn't keep a rolled back page in its
//         cache.
        
//         Also used by B+ tree files to ensure that deleted pages
//         are removed from the cache so they can be reused safely
//     */
//     public synchronized void discardPage(PageId pid) {
//         // some code goes here
//         // not necessary for lab1
//         if (this.lruCache.get(pid) != null) {
//             this.remove(this.lruCache.get(pid));
//         }
//         this.lruCache.remove(pid);
//     }

//     /**
//      * Flushes a certain page to disk
//      * @param pid an ID indicating the page to flush
//      */
//     private synchronized  void flushPage(PageId pid) throws IOException {
//         // some code goes here
//         // not necessary for lab1
//         // Retrieve the dirty page from the cache using the provided PageId
//         Page dirtyPage = this.lruCache.get(pid).getElement();
//         TransactionId dirtier = dirtyPage.isDirty();

//         // If the page is marked as dirty, proceed with flushing operations
//         if (dirtier != null) {
//             // Log the write operation before flushing to ensure durability
//             Database.getLogFile().logWrite(dirtier, dirtyPage.getBeforeImage(), dirtyPage);
//             Database.getLogFile().force();

//             // Write the dirty page back to the disk to synchronize the persistent storage
//             Database.getCatalog().getDatabaseFile(pid.getTableId()).writePage(dirtyPage);
//             // Clear the dirty mark post-flush to signify that the page is now clean
//             dirtyPage.markDirty(false, null);
//         }
//     }

//     /** Write all pages of the specified transaction to disk.
//      */
//     public synchronized  void flushPages(TransactionId tid) throws IOException {
//         // some code goes here
//         // not necessary for lab1|lab2
//     }

//     /**
//      * Discards a page from the buffer pool.
//      * Flushes the page to disk to ensure dirty pages are updated on disk.
//      */
//     private synchronized  void evictPage() throws DbException {
//         // some code goes here
//         // not necessary for lab1
//         // Start from the page just before the tail and traverse backwards
//         DoublyLinkedNode<Page> node = this.tail.getPredecessor();

//         // Continue looking for a non-dirty page to evict until the head is reached
//         while (!node.equals(this.head) && node.getElement().isDirty() != null) {
//             node = node.getPredecessor(); // Move to the previous node in the chain
//         }

//         // If the head is reached, it implies all pages are dirty and none can be evicted
//         if (node.equals(this.head)) {
//             throw new DbException("All pages are dirty");
//         } else {
//             // If a clean page is found, discard it from the cache
//             this.discardPage(node.getElement().getId());
//         }
//     }

// }




package simpledb;



import java.io.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BufferPool manages the reading and writing of pages into memory from
 * disk. Access methods call into it to retrieve pages, and it fetches
 * pages from the appropriate location.
 * <p>
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 *
 * @Threadsafe, all fields are final
 */
public class BufferPool {
    /**
     * Bytes per page, including header.
     */
    private static final int DEFAULT_PAGE_SIZE = 4096;

    private static int pageSize = DEFAULT_PAGE_SIZE;

    /**
     * Default number of pages passed to the constructor. This is used by
     * other classes. BufferPool should use the numPages argument to the
     * constructor instead.
     */
    public static final int DEFAULT_PAGES = 50;

    private final int numPages;

    private final ConcurrentHashMap<PageId, Node<Page>> lruCache;  // 防止锁的占用

    private final Node<Page> head;
    private final Node<Page> tail;  // lruCache的头和尾

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        // some code goes here
        this.numPages = numPages;
        this.lruCache = new ConcurrentHashMap<>();
        this.head = new Node<>();
        this.tail = new Node<>();
        this.head.setNext(this.tail);
        this.tail.setPrev(this.head);
    }

    public class Node<T> {
        private T data;
        private Node<T> next;
        private Node<T> prev;

        public Node() {
            this.next = null;
            this.prev = null;
        }

        public Node(T dataVal) {
            this.data = dataVal;
            this.next = null;
            this.prev = null;
        }

        public void setNext(Node<T> next) {
            this.next = next;
        }

        public void setPrev(Node<T> prev) {
            this.prev = prev;
        }

        public T getData() {
            return data;
        }

        public Node<T> getNext() {
            return next;
        }

        public Node<T> getPrev() {
            return prev;
        }
    }

    public void put(Node<Page> curNode) {
        // 向链表中添加
        curNode.setNext(this.head.getNext());
        curNode.setPrev(this.head);  // fix bug in lab3 exercise2
        this.head.getNext().setPrev(curNode);  // 不能忽略这一步
        this.head.setNext(curNode);
    }

    public void remove(Node<Page> curNode) {
        // 从链表中删除
        Node<Page> prevNode = curNode.getPrev();
        Node<Page> nextNode = curNode.getNext();
        prevNode.setNext(nextNode);
        nextNode.setPrev(prevNode);
    }

    public Node<Page> getTail() {
        return this.tail.getPrev();
    }

    public static int getPageSize() {
        return pageSize;
    }

    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void setPageSize(int pageSize) {
        BufferPool.pageSize = pageSize;
    }

    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void resetPageSize() {
        BufferPool.pageSize = DEFAULT_PAGE_SIZE;
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     * <p>
     * The retrieved page should be looked up in the buffer pool.  If it
     * is present, it should be returned.  If it is not present, it should
     * be added to the buffer pool and returned.  If there is insufficient
     * space in the buffer pool, a page should be evicted and the new page
     * should be added in its place.
     *
     * @param tid  the ID of the transaction requesting the page
     * @param pid  the ID of the requested page
     * @param perm the requested permissions on the page
     */
    public Page getPage(TransactionId tid, PageId pid, Permissions perm)
            throws TransactionAbortedException, DbException {
        // some code goes here
        

        Node<Page> curNode = this.lruCache.get(pid);
        if (curNode == null) {
            if (this.lruCache.size() == this.numPages) {
                evictPage();
            }
            curNode = new Node<>(Database.getCatalog().getDatabaseFile(pid.getTableId()).readPage(pid));
            this.put(curNode);
            this.lruCache.put(pid, curNode);
            // 从磁盘读出page（page有自己的table id，而table和dbfile一一对应，dbfile是与磁盘交互的接口）
            // 将载入buffer pool的page给到curPage
        } else {
            this.remove(curNode);
            this.put(curNode);
        }
        return curNode.getData();
    }

    /**
     * Releases the lock on a page.
     * Calling this is very risky, and may result in wrong behavior. Think hard
     * about who needs to call this and why, and why they can run the risk of
     * calling it.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param pid the ID of the page to unlock
     */
    public void unsafeReleasePage(TransactionId tid, PageId pid) {
        // some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) {
        // some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Return true if the specified transaction has a lock on the specified page
     */
    public boolean holdsLock(TransactionId tid, PageId p) {
        // some code goes here
        // not necessary for lab1|lab2
        return false;
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid    the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit) {
        // some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to and any other
     * pages that are updated (Lock acquisition is not needed for lab2).
     * May block if the lock(s) cannot be acquired.
     * <p>
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid     the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t       the tuple to add
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        DbFile dbFile = Database.getCatalog().getDatabaseFile(tableId);
        List<Page> curPage = dbFile.insertTuple(tid, t);
        for (Page page : curPage) {
            page.markDirty(true, tid);
            Node<Page> curNode = new Node<>(page);
            if (this.lruCache.get(page.getId()) != null) {
                // BTree的测试中，page可能会分裂，新分裂出来的page可能不在cache中，自然不用remove
                this.remove(this.lruCache.get(page.getId()));
            }
            this.put(curNode);
            this.lruCache.put(curNode.getData().getId(), curNode);
        }
    }

    /**
     * Remove the specified tuple from the buffer pool.
     * Will acquire a write lock on the page the tuple is removed from and any
     * other pages that are updated. May block if the lock(s) cannot be acquired.
     * <p>
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid the transaction deleting the tuple.
     * @param t   the tuple to delete
     */
    public void deleteTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        int tableid = t.getRecordId().getPageId().getTableId();
        DbFile dbFile = Database.getCatalog().getDatabaseFile(tableid);
        List<Page> curPage = dbFile.deleteTuple(tid, t);
        for (Page page : curPage) {
            page.markDirty(true, tid);
            Node<Page> curNode = new Node<>(page);
            if (this.lruCache.get(page.getId()) != null) {
                this.remove(this.lruCache.get(page.getId()));
            }
            this.put(curNode);
            this.lruCache.put(page.getId(), curNode);
        }
    }

    /**
     * Flush all dirty pages to disk.
     * NB: Be careful using this routine -- it writes dirty data to disk so will
     * break simpledb if running in NO STEAL mode.
     */
    public synchronized void flushAllPages() throws IOException {
        // some code goes here
        // not necessary for lab1
        for (Map.Entry<PageId, Node<Page>> entry : this.lruCache.entrySet()) {
            if (entry.getValue().getData().isDirty() != null) {
                this.flushPage(entry.getValue().getData().getId());
            }
        }
    }

    /**
     * Remove the specific page id from the buffer pool.
     * Needed by the recovery manager to ensure that the
     * buffer pool doesn't keep a rolled back page in its
     * cache.
     * <p>
     * Also used by B+ tree files to ensure that deleted pages
     * are removed from the cache so they can be reused safely
     */
    public synchronized void discardPage(PageId pid) {
        // some code goes here
        // not necessary for lab1
        if (this.lruCache.get(pid) != null) {
            this.remove(this.lruCache.get(pid));
        }
        this.lruCache.remove(pid);
    }

    /**
     * Flushes a certain page to disk
     *
     * @param pid an ID indicating the page to flush
     */
    private synchronized void flushPage(PageId pid) throws IOException {
        // some code goes here
        // not necessary for lab1
        Page dirtyPage = this.lruCache.get(pid).getData();
        Database.getCatalog().getDatabaseFile(pid.getTableId()).writePage(dirtyPage);  // 写入磁盘
        dirtyPage.markDirty(false, null);
    }

    /**
     * Write all pages of the specified transaction to disk.
     */
    public synchronized void flushPages(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     */
    private synchronized void evictPage() throws DbException {
        // some code goes here
        // not necessary for lab1
        Node<Page> node = this.tail.getPrev();
        while (!node.equals(this.head) && node.getData().isDirty() != null) {
            node = node.getPrev();
        }
        if (node.equals(this.head)) { // 全部都是脏页
            throw new DbException("all pages are dirty");
        } else {
            this.discardPage(node.getData().getId());
        }
    }

}