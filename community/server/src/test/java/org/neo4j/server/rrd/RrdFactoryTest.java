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
package org.neo4j.server.rrd;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.rrd4j.ConsolFun;
import org.rrd4j.DsType;
import org.rrd4j.core.RrdDb;
import org.rrd4j.core.RrdDef;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.logging.DevNullLoggingService;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.database.Database;
import org.neo4j.server.database.RrdDbWrapper;
import org.neo4j.server.database.WrappedDatabase;
import org.neo4j.server.web.ServerInternalSettings;
import org.neo4j.test.Mute;
import org.neo4j.test.TargetDirectory;
import org.neo4j.test.TestGraphDatabaseFactory;

import static java.lang.Double.NaN;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.neo4j.test.Mute.muteAll;

public class RrdFactoryTest
{
    private Config config;
    private Database db;
    private String storeDir;

    @Rule
    public final TargetDirectory.TestDirectory directory = TargetDirectory.testDirForTest( getClass() );
    @Rule
    public Mute mute = muteAll();

    @Before
    public void setUp() throws IOException
    {
        storeDir = directory.graphDbDir().getAbsolutePath();
        db = new WrappedDatabase( (GraphDatabaseAPI) new TestGraphDatabaseFactory().newEmbeddedDatabase( storeDir ) );
        config = new Config();
    }

    @Test
    public void shouldTakeDirectoryLocationFromConfig() throws Exception
    {
        // Given
        addProperty( Configurator.RRDB_LOCATION_PROPERTY_KEY, storeDir );
        TestableRrdFactory factory = createRrdFactory();

        // When
        factory.createRrdDbAndSampler( db, new NullJobScheduler() );

        // Then
        assertThat( factory.directoryUsed, is( storeDir ) );
    }

    @Test
    public void recreateDatabaseIfWrongStepsize() throws Exception
    {
        addProperty( Configurator.RRDB_LOCATION_PROPERTY_KEY, storeDir );
        TestableRrdFactory factory = createRrdFactory();

        factory.createRrdDbAndSampler( db, new NullJobScheduler() );

        assertThat( factory.directoryUsed, is( storeDir ) );
    }

    @Test
    public void shouldMoveAwayInvalidRrdFile() throws IOException
    {
        //Given
        File rrdDir = new File( directory.directory(), ServerInternalSettings.rrd_store.getDefaultValue() );
        assertTrue( rrdDir.mkdirs() );
        String rrdFilePath = new File( rrdDir, "rrd-test" ).getAbsolutePath();
        addProperty( Configurator.RRDB_LOCATION_PROPERTY_KEY, rrdFilePath );

        TestableRrdFactory factory = createRrdFactory();
        createInvalidRrdFile( rrdFilePath );

        //When
        RrdDbWrapper rrdDbAndSampler = factory.createRrdDbAndSampler( db, new NullJobScheduler() );

        //Then
        assertSubdirectoryExists( "rrd-test-invalid", factory.directoryUsed );

        rrdDbAndSampler.close();
    }

    private void createInvalidRrdFile( String rrdFilePath ) throws IOException
    {
        // create invalid rrd
        RrdDef rrdDef = new RrdDef( rrdFilePath, 3000 );
        rrdDef.addDatasource( "test", DsType.GAUGE, 1, NaN, NaN );
        rrdDef.addArchive( ConsolFun.AVERAGE, 0.2, 1, 1600 );
        RrdDb r = new RrdDb( rrdDef );
        r.close();
    }

    @Test
    public void shouldCreateRrdFileInTheConfiguredDirectory() throws Exception
    {
        // Given
        File rrdDir = new File( directory.directory(), ServerInternalSettings.rrd_store.getDefaultValue() );
        assertTrue( rrdDir.mkdirs() );
        String rrdFilePath = new File( rrdDir, "rrd" ).getAbsolutePath();
        addProperty( Configurator.RRDB_LOCATION_PROPERTY_KEY, rrdFilePath );
        TestableRrdFactory factory = createRrdFactory();

        // When
        factory.createRrdDbAndSampler( db, new NullJobScheduler() );

        //Then
        assertThat( factory.directoryUsed, is( rrdFilePath ) );
    }

    @Test
    public void shouldDeleteOldRrdFileFromDbDirectoryIfItExists() throws Exception
    {
        // given
        File oldRrdFile = new File( storeDir, "rrd" );
        assertTrue( oldRrdFile.createNewFile() );
        TestableRrdFactory factory = createRrdFactory();

        // When
        factory.createRrdDbAndSampler( db, new NullJobScheduler() );

        //Then
        assertFalse( oldRrdFile.exists() );
    }

    private void addProperty( String rrdbLocationPropertyKey, String expected )
    {
        Map<String,String> params = config.getParams();
        params.put( rrdbLocationPropertyKey, expected );
        config.applyChanges( params );
    }

    private void assertSubdirectoryExists( final String directoryThatShouldExist, String directoryUsed )
    {
        File parentFile = new File( directoryUsed ).getParentFile();
        String[] list = parentFile.list();

        for ( String aList : list )
        {
            if ( aList.startsWith( directoryThatShouldExist ) )
            {
                return;
            }
        }

        fail( String.format( "Didn't find [%s] in [%s]", directoryThatShouldExist, directoryUsed ) );
    }

    private TestableRrdFactory createRrdFactory()
    {
        return new TestableRrdFactory( config );
    }

    private static class TestableRrdFactory extends RrdFactory
    {
        public String directoryUsed;

        public TestableRrdFactory( Config config )
        {
            super( config, DevNullLoggingService.DEV_NULL );
        }

        @Override
        protected RrdDbWrapper createRrdb( File inDirectory, Sampleable... sampleables )
        {
            directoryUsed = inDirectory.getAbsolutePath();
            return super.createRrdb( inDirectory, sampleables );
        }
    }

    private static class NullJobScheduler implements JobScheduler
    {
        @Override
        public void scheduleAtFixedRate( Runnable job, String name, long delay, long period )
        {

        }
    }
}
