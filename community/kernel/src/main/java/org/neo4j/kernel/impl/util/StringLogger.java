/*
 * Copyright (c) 2002-2015 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.neo4j.helpers.Format;
import org.neo4j.helpers.collection.Visitor;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.kernel.logging.LogMarker;

import static org.neo4j.helpers.collection.IteratorUtil.loop;

public abstract class StringLogger
{
    public static final String DEFAULT_NAME = "messages.log";
    public static final String DEFAULT_ENCODING = "UTF-8";
    public static final StringLogger SYSTEM, SYSTEM_ERR;
    public static final StringLogger SYSTEM_DEBUG, SYSTEM_ERR_DEBUG;

    static
    {
        SYSTEM = instantiateStringLoggerForPrintStream( System.out, false );
        SYSTEM_ERR = instantiateStringLoggerForPrintStream( System.err, false );
        SYSTEM_DEBUG = instantiateStringLoggerForPrintStream( System.out, true );
        SYSTEM_ERR_DEBUG = instantiateStringLoggerForPrintStream( System.err, true );
    }

    private static ActualStringLogger instantiateStringLoggerForPrintStream( PrintStream stream,
            boolean debugEnabled )
    {
        PrintWriter writer;

        try
        {
            writer = new PrintWriter( new OutputStreamWriter( stream, DEFAULT_ENCODING ), true );
        }
        catch ( UnsupportedEncodingException e )
        {
            throw new RuntimeException( e );
        }

        return new ActualStringLogger( writer, debugEnabled )
        {
            @Override
            public void close()
            {
                // don't close System.out
            }
        };
    }

    public static final int DEFAULT_THRESHOLD_FOR_ROTATION = 100 * 1024 * 1024;
    private static final int NUMBER_OF_OLD_LOGS_TO_KEEP = 2;

    public interface LineLogger
    {
        void logLine( String line );
    }

    public static StringLogger logger( File logfile )
    {
        try
        {
            return new ActualStringLogger( new PrintWriter(
                    new OutputStreamWriter( new FileOutputStream( logfile, true), DEFAULT_ENCODING ) ), false );
        }
        catch ( IOException cause )
        {
            throw new RuntimeException( "Could not create log file: " + logfile, cause );
        }
    }

    public static StringLogger logger( FileSystemAbstraction fileSystem, File logFile,
            int rotationThreshold, boolean debugEnabled )
    {
        return new ActualStringLogger( fileSystem, logFile.getPath(), rotationThreshold, debugEnabled );
    }

    public static StringLogger loggerDirectory( FileSystemAbstraction fileSystem, File logDirectory )
    {
        return loggerDirectory( fileSystem, logDirectory, DEFAULT_THRESHOLD_FOR_ROTATION, false );
    }

    public static StringLogger loggerDirectory( FileSystemAbstraction fileSystem, File logDirectory,
            int rotationThreshold, boolean debugEnabled )
    {
        return new ActualStringLogger( fileSystem, new File( logDirectory, DEFAULT_NAME ).getPath(),
                rotationThreshold, debugEnabled );
    }

    public static StringLogger wrap( Writer writer )
    {
        return new ActualStringLogger(
                writer instanceof PrintWriter ? (PrintWriter) writer : new PrintWriter( writer ), false );
    }

    public static StringLogger wrap( final StringBuffer target )
    {
        return new ActualStringLogger( new PrintWriter( new Writer()
        {
            @Override
            public void write( char[] cbuf, int off, int len ) throws IOException
            {
                target.append( cbuf, off, len );
            }

            @Override
            public void write( int c ) throws IOException
            {
                target.appendCodePoint( c );
            }

            @Override
            public void write( char[] cbuf ) throws IOException
            {
                target.append( cbuf );
            }

            @Override
            public void write( String str ) throws IOException
            {
                target.append( str );
            }

            @Override
            public void write( String str, int off, int len ) throws IOException
            {
                target.append( str, off, len );
            }

            @Override
            public Writer append( char c ) throws IOException
            {
                target.append( c );
                return this;
            }

            @Override
            public Writer append( CharSequence csq ) throws IOException
            {
                target.append( csq );
                return this;
            }

            @Override
            public Writer append( CharSequence csq, int start, int end ) throws IOException
            {
                target.append( csq, start, end );
                return this;
            }

            @Override
            public void flush() throws IOException
            {
                // do nothing
            }

            @Override
            public void close() throws IOException
            {
                // do nothing
            }
        } ), false );
    }

    public static StringLogger tee( final StringLogger logger1, final StringLogger logger2 )
    {
        return new StringLogger()
        {
            @Override
            protected void doDebug( String msg, Throwable cause, boolean flush, LogMarker logMarker )
            {
                logger1.doDebug( msg, cause, flush, logMarker );
                logger2.doDebug( msg, cause, flush, logMarker );
            }

            @Override
            public void info( String msg, Throwable cause, boolean flush, LogMarker logMarker )
            {
                logger1.info( msg, cause, flush, logMarker );
                logger2.info( msg, cause, flush, logMarker );
            }

            @Override
            public void warn( String msg, Throwable cause, boolean flush, LogMarker logMarker )
            {
                logger1.warn( msg, cause, flush, logMarker );
                logger2.warn( msg, cause, flush, logMarker );
            }

            @Override
            public void error( String msg, Throwable cause, boolean flush, LogMarker logMarker )
            {
                logger1.error( msg, cause, flush, logMarker );
                logger2.error( msg, cause, flush, logMarker );
            }

            @Override
            public void logLongMessage( String msg, Visitor<LineLogger,RuntimeException> source, boolean flush )
            {
                logger1.logLongMessage( msg, source, flush );
                logger2.logLongMessage( msg, source, flush );
            }

            @Override
            public void addRotationListener( Runnable listener )
            {
                logger1.addRotationListener( listener );
                logger2.addRotationListener( listener );
            }

            @Override
            public void flush()
            {
                logger1.flush();
                logger2.flush();
            }

            @Override
            public void close()
            {
                logger1.close();
                logger2.close();
            }

            @Override
            protected void logLine( String line )
            {
                logger1.logLine( line );
                logger2.logLine( line );
            }
        };
    }

    /**
     * Create a StringLogger that only creates a file on the first attempt to write something to the log.
     */
    public static StringLogger lazyLogger( final File logFile )
    {
        return new StringLogger()
        {
            StringLogger logger = null;

            @Override
            public void doDebug( String msg, Throwable cause, boolean flush, LogMarker logMarker )
            {
                createLogger();
                logger.doDebug( msg, cause, flush, logMarker );
            }

            @Override
            public void info( String msg, Throwable cause, boolean flush, LogMarker logMarker )
            {
                createLogger();
                logger.info( msg, cause, flush, logMarker );
            }

            @Override
            public void warn( String msg, Throwable cause, boolean flush, LogMarker logMarker )
            {
                createLogger();
                logger.warn( msg, cause, flush, logMarker );
            }

            @Override
            public void error( String msg, Throwable cause, boolean flush, LogMarker logMarker )
            {
                createLogger();
                logger.error( msg, cause, flush, logMarker );
            }

            @Override
            public void logLongMessage( String msg, Visitor<LineLogger, RuntimeException> source, boolean flush )
            {
                createLogger();
                logger.logLongMessage( msg, source, flush );
            }

            @Override
            public void addRotationListener( Runnable listener )
            {
                createLogger();
                logger.addRotationListener( listener );
            }

            @Override
            public void flush()
            {
                createLogger();
                logger.flush();
            }

            @Override
            public void close()
            {
                if ( logger != null )
                {
                    logger.close();
                }
            }

            @Override
            protected void logLine( String line )
            {
                createLogger();
                logger.logLine( line );
            }

            private synchronized void createLogger()
            {
                if (logger == null){
                    logger = logger( logFile );
                }
            }
        };
    }

    public final void debug( String msg )
    {
        debug( msg, null );
    }

    public final void debug( String msg, boolean flush )
    {
        debug( msg, null, flush );
    }

    public final void debug( String msg, Throwable cause )
    {
        debug( msg, cause, false );
    }

    public final void debug( String msg, Throwable cause, boolean flush )
    {
        debug( msg, cause, flush, LogMarker.NO_MARK );
    }

    public final void debug( String msg, Throwable cause, boolean flush, LogMarker logMarker )
    {
        if ( isDebugEnabled() )
        {
            doDebug( msg, cause, flush, logMarker );
        }
    }

    protected abstract void doDebug( String msg, Throwable cause, boolean flush, LogMarker logMarker );

    public boolean isDebugEnabled()
    {
        return false;
    }

    public final void info( String msg )
    {
        info( msg, null );
    }

    public final void info( String msg, boolean flush )
    {
        info( msg, null, flush );
    }

    public final void info( String msg, Throwable cause )
    {
        info( msg, cause, false );
    }

    public final void info( String msg, Throwable cause, boolean flush )
    {
        info( msg, cause, flush, LogMarker.NO_MARK );
    }

    public abstract void info( String msg, Throwable cause, boolean flush, LogMarker logMarker );

    public final void warn( String msg )
    {
        warn( msg, null );
    }

    public final void warn( String msg, boolean flush )
    {
        warn( msg, null, flush );
    }

    public final void warn( String msg, Throwable cause )
    {
        warn( msg, cause, false );
    }

    public final void warn( String msg, Throwable cause, boolean flush )
    {
        warn( msg, cause, flush, LogMarker.NO_MARK );
    }

    public abstract void warn( String msg, Throwable cause, boolean flush, LogMarker logMarker );

    public final void error( String msg )
    {
        error( msg, null );
    }

    public final void error( String msg, boolean flush )
    {
        error( msg, null, flush );
    }

    public final void error( String msg, Throwable cause )
    {
        error( msg, cause, false );
    }

    public final void error( String msg, Throwable cause, boolean flush )
    {
        error( msg, cause, flush, LogMarker.NO_MARK );
    }

    public abstract void error( String msg, Throwable cause, boolean flush, LogMarker logMarker );

    public void logLongMessage( String msg, Visitor<LineLogger, RuntimeException> source )
    {
        logLongMessage( msg, source, false );
    }

    public void logLongMessage( String msg, Iterable<String> source )
    {
        logLongMessage( msg, source, false );
    }

    public void logLongMessage( String msg, Iterable<String> source, boolean flush )
    {
        logLongMessage( msg, source.iterator(), flush );
    }

    public void logLongMessage( String msg, Iterator<String> source )
    {
        logLongMessage( msg, source, false );
    }

    public void logLongMessage( String msg, final Iterator<String> source, boolean flush )
    {
        logLongMessage( msg, new Visitor<LineLogger, RuntimeException>()
        {
            @Override
            public boolean visit( LineLogger logger )
            {
                for ( String line : loop( source ) )
                {
                    logger.logLine( line );
                }
                return true;
            }
        }, flush );
    }

    public abstract void logLongMessage( String msg, Visitor<LineLogger, RuntimeException> source, boolean flush );

    public abstract void addRotationListener( Runnable listener );

    public abstract void flush();

    public abstract void close();

    protected abstract void logLine( String line );

    public static final StringLogger DEV_NULL = new StringLogger()
    {
        @Override
        public void doDebug( String msg, Throwable cause, boolean flush, LogMarker logMarker )
        {
        }

        @Override
        public void info( String msg, Throwable cause, boolean flush, LogMarker logMarker )
        {
        }

        @Override
        public void warn( String msg, Throwable cause, boolean flush, LogMarker logMarker )
        {
        }

        @Override
        public void error( String msg, Throwable cause, boolean flush, LogMarker logMarker )
        {
        }

        @Override
        public void logLongMessage( String msg, Visitor<LineLogger, RuntimeException> source, boolean flush )
        {
        }

        @Override
        protected void logLine( String line )
        {
        }

        @Override
        public void flush()
        {
        }

        @Override
        public void close()
        {
        }

        @Override
        public void addRotationListener( Runnable listener )
        {
        }
    };

    private static class ActualStringLogger extends StringLogger
    {
        private final static String encoding = "UTF-8";

        private PrintWriter out;
        private final Integer rotationThreshold;
        private final File file;
        private final List<Runnable> onRotation = new CopyOnWriteArrayList<Runnable>();
        private final FileSystemAbstraction fileSystem;
        private final boolean debugEnabled;

        private ActualStringLogger( FileSystemAbstraction fileSystem, String filename, int rotationThreshold,
                boolean debugEnabled )
        {
            this.fileSystem = fileSystem;
            this.rotationThreshold = rotationThreshold;
            this.debugEnabled = debugEnabled;
            try
            {
                file = new File( filename );
                if ( file.getParentFile() != null )
                {
                    fileSystem.mkdirs( file.getParentFile() );
                }
                instantiateWriter();
            }
            catch ( IOException e )
            {
                throw new RuntimeException( e );
            }
        }

        private ActualStringLogger( PrintWriter writer, boolean debugEnabled )
        {
            this.out = writer;
            this.rotationThreshold = null;
            this.file = null;
            this.fileSystem = null;
            this.debugEnabled = debugEnabled;
        }

        @Override
        public boolean isDebugEnabled()
        {
            return debugEnabled;
        }

        @Override
        protected void doDebug( String msg, Throwable cause, boolean flush, LogMarker logMarker )
        {
            doLogMessage( msg, cause, flush, logMarker, "DEBUG" );
        }

        @Override
        public void info( String msg, Throwable cause, boolean flush, LogMarker logMarker )
        {
            doLogMessage( msg, cause, flush, logMarker, "INFO" );
        }

        @Override
        public void warn( String msg, Throwable cause, boolean flush, LogMarker logMarker )
        {
            doLogMessage( msg, cause, flush, logMarker, "WARN" );
        }

        @Override
        public void error( String msg, Throwable cause, boolean flush, LogMarker logMarker )
        {
            doLogMessage( msg, cause, flush, logMarker, "ERROR" );
        }

        private synchronized void doLogMessage( String msg, Throwable cause, boolean flush, LogMarker ignored, String level )
        {
            String message = time();
            if ( cause != null )
            {
                message = message + " " + level + " [org.neo4j]: " + msg + " " + cause.getMessage();
            }
            else
            {
                message = message + " " + level + "  [org.neo4j]: " + msg;
            }

            out.println( message );
            if ( cause != null )
            {
                cause.printStackTrace( out );
            }

            if ( flush )
            {
                out.flush();
            }
            checkRotation();
        }

        @Override
        public void addRotationListener( Runnable trigger )
        {
            onRotation.add( trigger );
        }

        private void instantiateWriter() throws IOException
        {
            out = new PrintWriter( new OutputStreamWriter( fileSystem.openAsOutputStream( file, true ), encoding ) );
            for ( Runnable trigger : onRotation )
            {
                trigger.run();
            }
        }


        private String time()
        {
            return Format.date();
        }

        @Override
        public synchronized void logLongMessage( String msg, Visitor<LineLogger, RuntimeException> source, boolean flush )
        {
            out.println( time() + " INFO  [org.neo4j]: " + msg );
            source.visit( new LineLoggerImpl( this ) );
            if ( flush )
            {
                out.flush();
            }
            checkRotation();
        }

        @Override
        protected void logLine( String line )
        {
            out.println( "    " + line );
        }

        private volatile boolean doingRotation = false;

        private void checkRotation()
        {
            if ( rotationThreshold != null && fileSystem.getFileSize( file ) > rotationThreshold && !doingRotation )
            {
                doRotation();
            }
        }

        private void doRotation()
        {
            doingRotation = true;
            out.close();
            moveAwayFile();
            try
            {
                instantiateWriter();
            }
            catch ( IOException e )
            {
                throw new RuntimeException( e );
            }
            finally
            {
                doingRotation = false;
            }
        }

        /**
         * Will move:
         * messages.log.1 -> messages.log.2
         * messages.log   -> messages.log.1
         * <p>
         * Will delete (if exists):
         * messages.log.2
         */
        private void moveAwayFile()
        {
            File oldLogFile = new File( file.getParentFile(), file.getName() + "." + NUMBER_OF_OLD_LOGS_TO_KEEP );
            if ( fileSystem.fileExists( oldLogFile ) )
            {
                fileSystem.deleteFile( oldLogFile );
            }

            for ( int i = NUMBER_OF_OLD_LOGS_TO_KEEP - 1; i >= 0; i-- )
            {
                oldLogFile = new File( file.getParentFile(), file.getName() + (i == 0 ? "" : ("." + i)) );
                if ( fileSystem.fileExists( oldLogFile ) )
                {
                    try
                    {
                        fileSystem.renameFile( oldLogFile, new File( file.getParentFile(), file.getName() + "." + (i + 1) ) );
                    }
                    catch ( IOException e )
                    {
                        throw new RuntimeException( e );
                    }
                }
            }
        }

        @Override
        public void flush()
        {
            out.flush();
        }

        @Override
        public void close()
        {
            out.close();
        }

        @Override
        public String toString()
        {
            return "StringLogger[" + this.file + "]";
        }
    }

    protected static final class LineLoggerImpl implements LineLogger
    {
        private final StringLogger target;

        public LineLoggerImpl( StringLogger target )
        {
            this.target = target;
        }

        @Override
        public void logLine( String line )
        {
            target.logLine( line );
        }
    }
}
