package simpledb;

import java.io.*;
import java.util.*;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 * 
 * @see simpledb.HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {

    private File f;

    private TupleDesc td;
    /**
     * Constructs a heap file backed by the specified file.
     * 
     * @param f
     *            the file that stores the on-disk backing store for this heap
     *            file.
     */
    public HeapFile(File f, TupleDesc td) {
        // some code goes here
        this.f = f;
        this.td = td;
    }

    /**
     * Returns the File backing this HeapFile on disk.
     * 
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        // some code goes here
        return this.f;
        // return null;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     * 
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        // some code goes here
        return this.f.getAbsoluteFile().hashCode();
        // throw new UnsupportedOperationException("implement this");
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     * 
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        // some code goes here
        return this.td;
        // throw new UnsupportedOperationException("implement this");
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        // some code goes here
        int pageNo = pid.pageNumber();
        int pageSize = BufferPool.getPageSize();
        byte[] data = new byte[pageSize];
    
        try (RandomAccessFile curFile = new RandomAccessFile(this.f, "r")) {
            long offset = (long) pageNo * pageSize;
            curFile.seek(offset);
            // use readFully to ensure read page data completely
            curFile.readFully(data); 
            return new HeapPage(new HeapPageId(pid.getTableId(), pageNo), data);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Error reading page from disk", e);
        }
        // return null;
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        // some code goes here
        // not necessary for lab1
        RandomAccessFile file = new RandomAccessFile(this.f, "rw");  // 通过RandomAccessFile写
        file.seek((long) BufferPool.getPageSize() * page.getId().pageNumber());
        file.write(page.getPageData());
        file.close();
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        // some code goes here
        // get size of HeapFile in disk
        long len = f.length();  
        return (int) Math.floor(len * 1.0 / BufferPool.getPageSize());
        // return 0;
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        ArrayList<Page> res = new ArrayList<>();
        // 遍历这个DbFile的pages，看有没有空的slot
        for (int i = 0; i < this.numPages(); i++) {
            HeapPage curPage = (HeapPage) Database.getBufferPool().getPage(
                    tid,
                    new HeapPageId(this.getId(), i),
                    Permissions.READ_WRITE
            );
            if (curPage.getNumEmptySlots() > 0) {
                curPage.insertTuple(t);
                res.add(curPage);
                return res;
            }
        }
        HeapPage curPage = new HeapPage(
                new HeapPageId(this.getId(), this.numPages()),
                HeapPage.createEmptyPageData()
        );
        curPage.insertTuple(t);
        res.add(curPage);
        this.writePage(curPage);
        return res;
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        // some code goes here
        PageId pageId = t.getRecordId().getPageId();
        HeapPage curPage = (HeapPage) Database.getBufferPool().getPage(tid, pageId, Permissions.READ_WRITE);
        curPage.deleteTuple(t);

        ArrayList<Page> res = new ArrayList<>();
        res.add(curPage);
        return res;
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {
        // some code goes here
        // return null;
        return new DbFileIterator() {
            // initialize as -1，implying that iterator hasn't been open
            private int currentPageNo = -1; 
            private Iterator<Tuple> currentTupleIterator = null;
    
            @Override
            public void open() throws DbException, TransactionAbortedException {
                // begin with the first page
                currentPageNo = 0; 
                currentTupleIterator = getPageTupleIterator(currentPageNo);
            }
    
            @Override
            public boolean hasNext() throws DbException, TransactionAbortedException {
                if (currentPageNo == -1) return false; 
    
                // current iterator is empty or there is no more tuples trying to move to next page
                while ((currentTupleIterator == null || !currentTupleIterator.hasNext()) && currentPageNo < numPages()) {
                    currentPageNo++;
                    currentTupleIterator = getPageTupleIterator(currentPageNo);
                }
    
                return currentTupleIterator != null && currentTupleIterator.hasNext();
            }
    
            @Override
            public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return currentTupleIterator.next();
            }
    
            @Override
            public void rewind() throws DbException, TransactionAbortedException {
                close();
                open();
            }
    
            @Override
            public void close() {
                currentPageNo = -1;
                currentTupleIterator = null;
            }
    
            private Iterator<Tuple> getPageTupleIterator(int pageNo) throws TransactionAbortedException, DbException {
                // out of page range
                if (pageNo >= numPages()) return null; 
                PageId pid = new HeapPageId(getId(), pageNo);
                Page page = Database.getBufferPool().getPage(tid, pid, Permissions.READ_ONLY);
                return ((HeapPage) page).iterator();
            }
        };
    }
}

