package org.opengeo.data.importer;

import java.io.File;

import java.io.IOException;
import org.apache.commons.io.FilenameUtils;
import org.geoserver.ows.util.ResponseUtils;

public class FileData extends ImportData {

    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    /** the file handle*/
    protected File file;

    public FileData(File file) {
        this.file = file;
    }

    public static FileData createFromFile(File file) throws IOException {
        if (file.isDirectory()) {
            return new Directory(file);
        }

        if (new VFSWorker().canHandle(file)) {
            return new Archive(file);
        }

        return new SpatialFile(file);
    }
    public File getFile() {
        return file;
    }

    @Override
    public String getName() {
        return FilenameUtils.getBaseName(file.getName());
    }

    @Override
    public void cleanup() throws IOException {
        if (file.exists()) {
            if (!file.delete()) {
                throw new IOException("Unable to delete " + file.getAbsolutePath());
            }
        }
    }

    public String relativePath(Directory dir) throws IOException {
        String dp = dir.getFile().getCanonicalPath();
        String fp = getFile().getCanonicalPath();

        if (fp.startsWith(dp)) {
            String left = fp.substring(dp.length());
            return ResponseUtils.appendPath(dir.getFile().getName(), left);
            //return  + File.separator + left;
        }
        return null;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((file == null) ? 0 : file.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!getClass().isInstance(obj) && !obj.getClass().isInstance(this)) {
            return false;
        }
        FileData other = (FileData) obj;
        if (file == null) {
            if (other.file != null)
                return false;
        } else if (!file.equals(other.file))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return file.getPath();
    }
}