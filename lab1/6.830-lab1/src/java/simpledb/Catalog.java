package simpledb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Catalog keeps track of all available tables in the database and their
 * associated schemas.
 * For now, this is a stub catalog that must be populated with tables by a
 * user program before it can be used -- eventually, this should be converted
 * to a catalog that reads a catalog table from disk.
 * 
 * @Threadsafe
 */
public class Catalog {
    private static ConcurrentHashMap<String, DbFile> tables;
    private static ConcurrentHashMap<DbFile, String> pkey;

    /**
     * Constructor.
     * Creates a new, empty catalog.
     */
    public Catalog() {
        // some code goes here
        this.tables = new ConcurrentHashMap<String, DbFile>();
        this.pkey   = new ConcurrentHashMap<DbFile, String>();
    }

    /**
     * Add a new table to the catalog.
     * This table's contents are stored in the specified DbFile.
     * @param file the contents of the table to add;  file.getId() is the identfier of
     *    this file/tupledesc param for the calls getTupleDesc and getFile
     * @param name the name of the table -- may be an empty string.  May not be null.  If a name
     * @param pkeyField the name of the primary key field
     * conflict exists, use the last table to be added as the table for a given name.
     */
    public void addTable(DbFile file, String name, String pkeyField) {
        // some code goes here
        this.tables.putIfAbsent(name, file);
        if (!(pkeyField == null || pkeyField == "")){
            this.pkey.putIfAbsent(file, pkeyField);
        }
    }

    public void addTable(DbFile file, String name) {
        addTable(file, name, "");
    }

    /**
     * Add a new table to the catalog.
     * This table has tuples formatted using the specified TupleDesc and its
     * contents are stored in the specified DbFile.
     * @param file the contents of the table to add;  file.getId() is the identfier of
     *    this file/tupledesc param for the calls getTupleDesc and getFile
     */
    public void addTable(DbFile file) {
        addTable(file, (UUID.randomUUID()).toString());
    }

    /**
     * Return the id of the table with a specified name,
     * @throws NoSuchElementException if the table doesn't exist
     */
    public int getTableId(String name) throws NoSuchElementException {
        if (name == null || !(this.tables.containsKey(name))) {
            throw new NoSuchElementException();
        } else{
            return this.tables.get(name).getId();
        }
    }

    /**
     * Returns the tuple descriptor (schema) of the specified table
     * @param tableid The id of the table, as specified by the DbFile.getId()
     *     function passed to addTable
     * @throws NoSuchElementException if the table doesn't exist
     */
    public TupleDesc getTupleDesc(int tableid) throws NoSuchElementException {
        Object values[] = this.tables.values().toArray();

        for(int i = 0; i < values.length; i++){
            DbFile obj = (DbFile)values[i];
            if (obj.getId() == tableid){
                return obj.getTupleDesc();
            }
        }
        throw new NoSuchElementException();
    }

    /**
     * Returns the DbFile that can be used to read the contents of the
     * specified table.
     * @param tableid The id of the table, as specified by the DbFile.getId()
     *     function passed to addTable
     */
    public DbFile getDatabaseFile(int tableid) throws NoSuchElementException {
        Object values[] = this.tables.values().toArray();

        for(int i = 0; i < values.length; i++){
            DbFile obj = (DbFile)values[i];
            if (obj.getId() == tableid){
                return obj;
            }
        }
        throw new NoSuchElementException();
    }

    public String getPrimaryKey(int tableid) {
        DbFile dbFile = getDatabaseFile(tableid);
        if (dbFile == null || !(this.pkey.containsKey(dbFile))) {
            return null;
        } else{
            return this.pkey.get(dbFile);
        }
    }

    //helper class for iterator<Integer>
    private class Iter implements Iterator<Integer>{
        int cursor  = 0;
        int lastRet = -1;

        Object values[] = Catalog.this.tables.values().toArray();

        public boolean hasNext(){
            return cursor != Catalog.this.tables.size();
        }

        public Integer next(){
            if(! hasNext()){
                throw new NoSuchElementException();
            }

            int i = cursor;
            Object[] obj = values;
            cursor += 1;
            lastRet =  i;
            return ((DbFile) obj[lastRet]).getId();
        }
    }

    public Iterator<Integer> tableIdIterator() {
        return new Iter();
    }

    public String getTableName(int id) {
        Object values[] = this.tables.values().toArray();

        for(int i = 0; i < values.length; i++){
            DbFile obj = (DbFile)values[i];
            if (obj.getId() == id){
                for (Object name : this.tables.keySet()) {
                    if (this.tables.get(name).equals(obj)) {
                        return (String)name;
                    }
                }
                return null;
            }
        }
        throw new NoSuchElementException();
    }
    
    /** Delete all tables from the catalog */
    public void clear() {
        // some code goes here
        this.tables.clear();
        this.pkey.clear();
    }
    
    /**
     * Reads the schema from a file and creates the appropriate tables in the database.
     * @param catalogFile
     */
    public void loadSchema(String catalogFile) {
        String line = "";
        String baseFolder=new File(new File(catalogFile).getAbsolutePath()).getParent();
        try {
            BufferedReader br = new BufferedReader(new FileReader(new File(catalogFile)));
            
            while ((line = br.readLine()) != null) {
                //assume line is of the format name (field type, field type, ...)
                String name = line.substring(0, line.indexOf("(")).trim();
                //System.out.println("TABLE NAME: " + name);
                String fields = line.substring(line.indexOf("(") + 1, line.indexOf(")")).trim();
                String[] els = fields.split(",");
                ArrayList<String> names = new ArrayList<String>();
                ArrayList<Type> types = new ArrayList<Type>();
                String primaryKey = "";
                for (String e : els) {
                    String[] els2 = e.trim().split(" ");
                    names.add(els2[0].trim());
                    if (els2[1].trim().toLowerCase().equals("int"))
                        types.add(Type.INT_TYPE);
                    else if (els2[1].trim().toLowerCase().equals("string"))
                        types.add(Type.STRING_TYPE);
                    else {
                        System.out.println("Unknown type " + els2[1]);
                        System.exit(0);
                    }
                    if (els2.length == 3) {
                        if (els2[2].trim().equals("pk"))
                            primaryKey = els2[0].trim();
                        else {
                            System.out.println("Unknown annotation " + els2[2]);
                            System.exit(0);
                        }
                    }
                }
                Type[] typeAr = types.toArray(new Type[0]);
                String[] namesAr = names.toArray(new String[0]);
                TupleDesc t = new TupleDesc(typeAr, namesAr);
                HeapFile tabHf = new HeapFile(new File(baseFolder+"/"+name + ".dat"), t);
                addTable(tabHf,name,primaryKey);
                System.out.println("Added table : " + name + " with schema " + t);
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        } catch (IndexOutOfBoundsException e) {
            System.out.println ("Invalid catalog entry : " + line);
            System.exit(0);
        }
    }
}

