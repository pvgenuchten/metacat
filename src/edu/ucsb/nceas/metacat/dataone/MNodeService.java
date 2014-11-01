/**
 *  '$RCSfile$'
 *  Copyright: 2000-2011 Regents of the University of California and the
 *              National Center for Ecological Analysis and Synthesis
 *
 *   '$Author:  $'
 *     '$Date:  $'
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package edu.ucsb.nceas.metacat.dataone;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.UUID;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.dataone.client.v2.CNode;
import org.dataone.client.v2.itk.D1Client;
import org.dataone.client.v2.MNode;
import org.dataone.client.v2.formats.ObjectFormatCache;
import org.dataone.client.auth.CertificateManager;
import org.dataone.client.v2.formats.ObjectFormatInfo;
import org.dataone.configuration.Settings;
import org.dataone.ore.ResourceMapFactory;
import org.dataone.service.exceptions.BaseException;
import org.dataone.service.exceptions.IdentifierNotUnique;
import org.dataone.service.exceptions.InsufficientResources;
import org.dataone.service.exceptions.InvalidRequest;
import org.dataone.service.exceptions.InvalidSystemMetadata;
import org.dataone.service.exceptions.InvalidToken;
import org.dataone.service.exceptions.NotAuthorized;
import org.dataone.service.exceptions.NotFound;
import org.dataone.service.exceptions.NotImplemented;
import org.dataone.service.exceptions.ServiceFailure;
import org.dataone.service.exceptions.SynchronizationFailed;
import org.dataone.service.exceptions.UnsupportedType;
import org.dataone.service.mn.tier1.v2.MNCore;
import org.dataone.service.mn.tier1.v2.MNRead;
import org.dataone.service.mn.tier2.v2.MNAuthorization;
import org.dataone.service.mn.tier3.v2.MNStorage;
import org.dataone.service.mn.tier4.v2.MNReplication;
import org.dataone.service.mn.v2.MNPackage;
import org.dataone.service.mn.v2.MNQuery;
import org.dataone.service.mn.v2.MNView;
import org.dataone.service.types.v1.AccessRule;
import org.dataone.service.types.v1.Checksum;
import org.dataone.service.types.v1.DescribeResponse;
import org.dataone.service.types.v1.Event;
import org.dataone.service.types.v1.Identifier;
import org.dataone.service.types.v2.Log;
import org.dataone.service.types.v2.LogEntry;
import org.dataone.service.types.v2.OptionList;
import org.dataone.service.types.v1.MonitorInfo;
import org.dataone.service.types.v1.MonitorList;
import org.dataone.service.types.v2.Node;
import org.dataone.service.types.v2.NodeList;
import org.dataone.service.types.v1.NodeReference;
import org.dataone.service.types.v1.NodeState;
import org.dataone.service.types.v1.NodeType;
import org.dataone.service.types.v2.ObjectFormat;
import org.dataone.service.types.v1.ObjectFormatIdentifier;
import org.dataone.service.types.v1.ObjectList;
import org.dataone.service.types.v1.Permission;
import org.dataone.service.types.v1.Ping;
import org.dataone.service.types.v1.ReplicationStatus;
import org.dataone.service.types.v1.Schedule;
import org.dataone.service.types.v1.Service;
import org.dataone.service.types.v1.Services;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.Synchronization;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.types.v1.util.AuthUtils;
import org.dataone.service.types.v1.util.ChecksumUtil;
import org.dataone.service.types.v1_1.QueryEngineDescription;
import org.dataone.service.types.v1_1.QueryEngineList;
import org.dataone.service.types.v1_1.QueryField;
import org.dataone.service.util.Constants;
import org.dataone.service.util.TypeMarshaller;
import org.dspace.foresite.OREException;
import org.dspace.foresite.OREParserException;
import org.dspace.foresite.ORESerialiserException;
import org.dspace.foresite.ResourceMap;
import org.ecoinformatics.datamanager.parser.DataPackage;
import org.ecoinformatics.datamanager.parser.Entity;
import org.ecoinformatics.datamanager.parser.generic.DataPackageParserInterface;
import org.ecoinformatics.datamanager.parser.generic.Eml200DataPackageParser;

import edu.ucsb.nceas.ezid.EZIDException;
import edu.ucsb.nceas.metacat.DBQuery;
import edu.ucsb.nceas.metacat.DBTransform;
import edu.ucsb.nceas.metacat.EventLog;
import edu.ucsb.nceas.metacat.IdentifierManager;
import edu.ucsb.nceas.metacat.McdbDocNotFoundException;
import edu.ucsb.nceas.metacat.MetaCatServlet;
import edu.ucsb.nceas.metacat.MetacatHandler;
import edu.ucsb.nceas.metacat.common.query.EnabledQueryEngines;
import edu.ucsb.nceas.metacat.common.query.stream.ContentTypeByteArrayInputStream;
import edu.ucsb.nceas.metacat.dataone.hazelcast.HazelcastService;
import edu.ucsb.nceas.metacat.index.MetacatSolrEngineDescriptionHandler;
import edu.ucsb.nceas.metacat.index.MetacatSolrIndex;
import edu.ucsb.nceas.metacat.properties.PropertyService;
import edu.ucsb.nceas.metacat.shared.MetacatUtilException;
import edu.ucsb.nceas.metacat.util.DeleteOnCloseFileInputStream;
import edu.ucsb.nceas.metacat.util.DocumentUtil;
import edu.ucsb.nceas.metacat.util.SkinUtil;
import edu.ucsb.nceas.metacat.util.SystemUtil;
import edu.ucsb.nceas.utilities.PropertyNotFoundException;
import edu.ucsb.nceas.utilities.XMLUtilities;
import edu.ucsb.nceas.utilities.export.HtmlToPdf;
import gov.loc.repository.bagit.Bag;
import gov.loc.repository.bagit.BagFactory;
import gov.loc.repository.bagit.writer.impl.ZipWriter;

/**
 * Represents Metacat's implementation of the DataONE Member Node 
 * service API. Methods implement the various MN* interfaces, and methods common
 * to both Member Node and Coordinating Node interfaces are found in the
 * D1NodeService base class.
 * 
 * Implements:
 * MNCore.ping()
 * MNCore.getLogRecords()
 * MNCore.getObjectStatistics()
 * MNCore.getOperationStatistics()
 * MNCore.getStatus()
 * MNCore.getCapabilities()
 * MNRead.get()
 * MNRead.getSystemMetadata()
 * MNRead.describe()
 * MNRead.getChecksum()
 * MNRead.listObjects()
 * MNRead.synchronizationFailed()
 * MNAuthorization.isAuthorized()
 * MNAuthorization.setAccessPolicy()
 * MNStorage.create()
 * MNStorage.update()
 * MNStorage.delete()
 * MNReplication.replicate()
 * 
 */
public class MNodeService extends D1NodeService 
    implements MNAuthorization, MNCore, MNRead, MNReplication, MNStorage, MNQuery, MNView, MNPackage {

    //private static final String PATHQUERY = "pathquery";
	public static final String UUID_SCHEME = "UUID";
	public static final String DOI_SCHEME = "DOI";
	private static final String UUID_PREFIX = "urn:uuid:";

	/* the logger instance */
    private Logger logMetacat = null;
    
    /* A reference to a remote Memeber Node */
    private MNode mn;
    
    /* A reference to a Coordinating Node */
    private CNode cn;


    /**
     * Singleton accessor to get an instance of MNodeService.
     * 
     * @return instance - the instance of MNodeService
     */
    public static MNodeService getInstance(HttpServletRequest request) {
        return new MNodeService(request);
    }

    /**
     * Constructor, private for singleton access
     */
    private MNodeService(HttpServletRequest request) {
        super(request);
        logMetacat = Logger.getLogger(MNodeService.class);
        
        // set the Member Node certificate file location
        CertificateManager.getInstance().setCertificateLocation(Settings.getConfiguration().getString("D1Client.certificate.file"));
    }

    /**
     * Deletes an object from the Member Node, where the object is either a 
     * data object or a science metadata object.
     * 
     * @param session - the Session object containing the credentials for the Subject
     * @param pid - The object identifier to be deleted
     * 
     * @return pid - the identifier of the object used for the deletion
     * 
     * @throws InvalidToken
     * @throws ServiceFailure
     * @throws NotAuthorized
     * @throws NotFound
     * @throws NotImplemented
     * @throws InvalidRequest
     */
    @Override
    public Identifier delete(Session session, Identifier pid) 
        throws InvalidToken, ServiceFailure, NotAuthorized, NotFound, NotImplemented {

    	// only admin of  the MN or the CN is allowed a full delete
        boolean allowed = false;
        allowed = isAdminAuthorized(session);
        
        //check if it is the authoritative member node
        if(!allowed) {
            allowed = isAuthoritativeMNodeAdmin(session, pid);
        }
        
        if (!allowed) { 
            throw new NotAuthorized("1320", "The provided identity does not have " + "permission to delete objects on the Node.");
        }
    	
    	// defer to superclass implementation
        return super.delete(session, pid);
    }

    /**
     * Updates an existing object by creating a new object identified by 
     * newPid on the Member Node which explicitly obsoletes the object 
     * identified by pid through appropriate changes to the SystemMetadata 
     * of pid and newPid
     * 
     * @param session - the Session object containing the credentials for the Subject
     * @param pid - The identifier of the object to be updated
     * @param object - the new object bytes
     * @param sysmeta - the new system metadata describing the object
     * 
     * @return newPid - the identifier of the new object
     * 
     * @throws InvalidToken
     * @throws ServiceFailure
     * @throws NotAuthorized
     * @throws NotFound
     * @throws NotImplemented
     * @throws IdentifierNotUnique
     * @throws UnsupportedType
     * @throws InsufficientResources
     * @throws InvalidSystemMetadata
     * @throws InvalidRequest
     */
    @Override
    public Identifier update(Session session, Identifier pid, InputStream object, 
        Identifier newPid, SystemMetadata sysmeta) 
        throws InvalidToken, ServiceFailure, NotAuthorized, IdentifierNotUnique, 
        UnsupportedType, InsufficientResources, NotFound, 
        InvalidSystemMetadata, NotImplemented, InvalidRequest {

        String localId = null;
        boolean allowed = false;
        boolean isScienceMetadata = false;
        
        if (session == null) {
        	throw new InvalidToken("1210", "No session has been provided");
        }
        Subject subject = session.getSubject();

        // verify the pid is valid format
        if (!isValidIdentifier(pid)) {
        	throw new InvalidRequest("1202", "The provided identifier is invalid.");
        }

        // check for the existing identifier
        try {
            localId = IdentifierManager.getInstance().getLocalId(pid.getValue());
            
        } catch (McdbDocNotFoundException e) {
            throw new InvalidRequest("1202", "The object with the provided " + 
                "identifier was not found.");
            
        }
        
        // set the originating node
        NodeReference originMemberNode = this.getCapabilities().getIdentifier();
        sysmeta.setOriginMemberNode(originMemberNode);
        
        // set the submitter to match the certificate
        sysmeta.setSubmitter(subject);
        // set the dates
        Date now = Calendar.getInstance().getTime();
        sysmeta.setDateSysMetadataModified(now);
        sysmeta.setDateUploaded(now);
        
        // make sure serial version is set to something
        BigInteger serialVersion = sysmeta.getSerialVersion();
        if (serialVersion == null) {
        	sysmeta.setSerialVersion(BigInteger.ZERO);
        }

        // does the subject have WRITE ( == update) priveleges on the pid?
        allowed = isAuthorized(session, pid, Permission.WRITE);

        if (allowed) {
        	
        	// check quality of SM
        	if (sysmeta.getObsoletedBy() != null) {
        		throw new InvalidSystemMetadata("1300", "Cannot include obsoletedBy when updating object");
        	}
        	if (sysmeta.getObsoletes() != null && !sysmeta.getObsoletes().getValue().equals(pid.getValue())) {
        		throw new InvalidSystemMetadata("1300", "The identifier provided in obsoletes does not match old Identifier");
        	}

            // get the existing system metadata for the object
            SystemMetadata existingSysMeta = getSystemMetadata(session, pid);

            // check for previous update
            // see: https://redmine.dataone.org/issues/3336
            Identifier existingObsoletedBy = existingSysMeta.getObsoletedBy();
            if (existingObsoletedBy != null) {
            	throw new InvalidRequest("1202", 
            			"The previous identifier has already been made obsolete by: " + existingObsoletedBy.getValue());
            }

            isScienceMetadata = isScienceMetadata(sysmeta);

            // do we have XML metadata or a data object?
            if (isScienceMetadata) {

                // update the science metadata XML document
                // TODO: handle non-XML metadata/data documents (like netCDF)
                // TODO: don't put objects into memory using stream to string
                //String objectAsXML = "";
                try {
                    //objectAsXML = IOUtils.toString(object, "UTF-8");
                    // give the old pid so we can calculate the new local id 
                    localId = insertOrUpdateDocument(object, "UTF-8", pid, session, "update");
                    // register the newPid and the generated localId
                    if (newPid != null) {
                        IdentifierManager.getInstance().createMapping(newPid.getValue(), localId);

                    }

                } catch (IOException e) {
                    String msg = "The Node is unable to create the object. " + "There was a problem converting the object to XML";
                    logMetacat.info(msg);
                    throw new ServiceFailure("1310", msg + ": " + e.getMessage());

                }

            } else {

                // update the data object
                localId = insertDataObject(object, newPid, session);

            }
            
            // add the newPid to the obsoletedBy list for the existing sysmeta
            existingSysMeta.setObsoletedBy(newPid);

            // then update the existing system metadata
            updateSystemMetadata(existingSysMeta);

            // prep the new system metadata, add pid to the affected lists
            sysmeta.setObsoletes(pid);
            //sysmeta.addDerivedFrom(pid);

            // and insert the new system metadata
            insertSystemMetadata(sysmeta);

            // log the update event
            EventLog.getInstance().log(request.getRemoteAddr(), request.getHeader("User-Agent"), subject.getValue(), localId, Event.UPDATE.toString());
            
            // attempt to register the identifier - it checks if it is a doi
            try {
    			DOIService.getInstance().registerDOI(sysmeta);
    		} catch (Exception e) {
                throw new ServiceFailure("1190", "Could not register DOI: " + e.getMessage());
    		}

        } else {
            throw new NotAuthorized("1200", "The provided identity does not have " + "permission to UPDATE the object identified by " + pid.getValue()
                    + " on the Member Node.");
        }

        return newPid;
    }

    public Identifier create(Session session, Identifier pid, InputStream object, SystemMetadata sysmeta) throws InvalidToken, ServiceFailure, NotAuthorized,
            IdentifierNotUnique, UnsupportedType, InsufficientResources, InvalidSystemMetadata, NotImplemented, InvalidRequest {

        // check for null session
        if (session == null) {
          throw new InvalidToken("1110", "Session is required to WRITE to the Node.");
        }
        // set the submitter to match the certificate
        sysmeta.setSubmitter(session.getSubject());
        // set the originating node
        NodeReference originMemberNode = this.getCapabilities().getIdentifier();
        sysmeta.setOriginMemberNode(originMemberNode);
        sysmeta.setArchived(false);

        // set the dates
        Date now = Calendar.getInstance().getTime();
        sysmeta.setDateSysMetadataModified(now);
        sysmeta.setDateUploaded(now);
        
        // set the serial version
        sysmeta.setSerialVersion(BigInteger.ZERO);

        // check that we are not attempting to subvert versioning
        if (sysmeta.getObsoletes() != null && sysmeta.getObsoletes().getValue() != null) {
            throw new InvalidSystemMetadata("1180", 
              "The supplied system metadata is invalid. " +
              "The obsoletes field cannot have a value when creating entries.");
        }
        
        if (sysmeta.getObsoletedBy() != null && sysmeta.getObsoletedBy().getValue() != null) {
            throw new InvalidSystemMetadata("1180", 
              "The supplied system metadata is invalid. " +
              "The obsoletedBy field cannot have a value when creating entries.");
        }

        // call the shared impl
        Identifier resultPid = super.create(session, pid, object, sysmeta);
        
        // attempt to register the identifier - it checks if it is a doi
        try {
			DOIService.getInstance().registerDOI(sysmeta);
		} catch (Exception e) {
			ServiceFailure sf = new ServiceFailure("1190", "Could not register DOI: " + e.getMessage());
			sf.initCause(e);
            throw sf;
		}
        
        // return 
		return resultPid ;
    }

    /**
     * Called by a Coordinating Node to request that the Member Node create a 
     * copy of the specified object by retrieving it from another Member 
     * Node and storing it locally so that it can be made accessible to 
     * the DataONE system.
     * 
     * @param session - the Session object containing the credentials for the Subject
     * @param sysmeta - Copy of the CN held system metadata for the object
     * @param sourceNode - A reference to node from which the content should be 
     *                     retrieved. The reference should be resolved by 
     *                     checking the CN node registry.
     * 
     * @return true if the replication succeeds
     * 
     * @throws ServiceFailure
     * @throws NotAuthorized
     * @throws NotImplemented
     * @throws UnsupportedType
     * @throws InsufficientResources
     * @throws InvalidRequest
     */
    @Override
    public boolean replicate(Session session, SystemMetadata sysmeta,
            NodeReference sourceNode) throws NotImplemented, ServiceFailure,
            NotAuthorized, InvalidRequest, InsufficientResources,
            UnsupportedType {

        if (session != null && sysmeta != null && sourceNode != null) {
            logMetacat.info("MNodeService.replicate() called with parameters: \n" +
                            "\tSession.Subject      = "                           +
                            session.getSubject().getValue() + "\n"                +
                            "\tidentifier           = "                           + 
                            sysmeta.getIdentifier().getValue()                    +
                            "\n" + "\tSource NodeReference ="                     +
                            sourceNode.getValue());
        }
        boolean result = false;
        String nodeIdStr = null;
        NodeReference nodeId = null;

        // get the referenced object
        Identifier pid = sysmeta.getIdentifier();

        // get from the membernode
        // TODO: switch credentials for the server retrieval?
        this.mn = D1Client.getMN(sourceNode);
        this.cn = D1Client.getCN();
        InputStream object = null;
        Session thisNodeSession = null;
        SystemMetadata localSystemMetadata = null;
        BaseException failure = null;
        String localId = null;
        
        // TODO: check credentials
        // cannot be called by public
        if (session == null || session.getSubject() == null) {
            String msg = "No session was provided to replicate identifier " +
            sysmeta.getIdentifier().getValue();
            logMetacat.error(msg);
            throw new NotAuthorized("2152", msg);
            
        }


        // get the local node id
        try {
            nodeIdStr = PropertyService.getProperty("dataone.nodeId");
            nodeId = new NodeReference();
            nodeId.setValue(nodeIdStr);

        } catch (PropertyNotFoundException e1) {
            String msg = "Couldn't get dataone.nodeId property: " + e1.getMessage();
            failure = new ServiceFailure("2151", msg);
            //setReplicationStatus(thisNodeSession, pid, nodeId, ReplicationStatus.FAILED, failure);
            logMetacat.error(msg);
            //return true;
            throw new ServiceFailure("2151", msg);

        }
        

        try {
            // do we already have a replica?
            try {
                localId = IdentifierManager.getInstance().getLocalId(pid.getValue());
                // if we have a local id, get the local object
                try {
                    object = MetacatHandler.read(localId);
                } catch (Exception e) {
                	// NOTE: we may already know about this ID because it could be a data file described by a metadata file
                	// https://redmine.dataone.org/issues/2572
                	// TODO: fix this so that we don't prevent ourselves from getting replicas
                	
                    // let the CN know that the replication failed
                	logMetacat.warn("Object content not found on this node despite having localId: " + localId);
                	String msg = "Can't read the object bytes properly, replica is invalid.";
                    ServiceFailure serviceFailure = new ServiceFailure("2151", msg);
                    setReplicationStatus(thisNodeSession, pid, nodeId, ReplicationStatus.FAILED, serviceFailure);
                    logMetacat.warn(msg);
                    throw serviceFailure;
                    
                }

            } catch (McdbDocNotFoundException e) {
                logMetacat.info("No replica found. Continuing.");
                
            }
            
            // no local replica, get a replica
            if ( object == null ) {
                // session should be null to use the default certificate
                // location set in the Certificate manager
                object = mn.getReplica(thisNodeSession, pid);
                logMetacat.info("MNodeService.getReplica() called for identifier "
                                + pid.getValue());

            }

        } catch (InvalidToken e) {            
            String msg = "Could not retrieve object to replicate (InvalidToken): "+ e.getMessage();
            failure = new ServiceFailure("2151", msg);
            setReplicationStatus(thisNodeSession, pid, nodeId, ReplicationStatus.FAILED, failure);
            logMetacat.error(msg);
            throw new ServiceFailure("2151", msg);

        } catch (NotFound e) {
            String msg = "Could not retrieve object to replicate (NotFound): "+ e.getMessage();
            failure = new ServiceFailure("2151", msg);
            setReplicationStatus(thisNodeSession, pid, nodeId, ReplicationStatus.FAILED, failure);
            logMetacat.error(msg);
            throw new ServiceFailure("2151", msg);

        } catch (NotAuthorized e) {
            String msg = "Could not retrieve object to replicate (NotAuthorized): "+ e.getMessage();
            failure = new ServiceFailure("2151", msg);
            setReplicationStatus(thisNodeSession, pid, nodeId, ReplicationStatus.FAILED, failure);
            logMetacat.error(msg);
            throw new ServiceFailure("2151", msg);
        } catch (NotImplemented e) {
            String msg = "Could not retrieve object to replicate (mn.getReplica NotImplemented): "+ e.getMessage();
            failure = new ServiceFailure("2151", msg);
            setReplicationStatus(thisNodeSession, pid, nodeId, ReplicationStatus.FAILED, failure);
            logMetacat.error(msg);
            throw new ServiceFailure("2151", msg);
        } catch (ServiceFailure e) {
            String msg = "Could not retrieve object to replicate (ServiceFailure): "+ e.getMessage();
            failure = new ServiceFailure("2151", msg);
            setReplicationStatus(thisNodeSession, pid, nodeId, ReplicationStatus.FAILED, failure);
            logMetacat.error(msg);
            throw new ServiceFailure("2151", msg);
        } catch (InsufficientResources e) {
            String msg = "Could not retrieve object to replicate (InsufficientResources): "+ e.getMessage();
            failure = new ServiceFailure("2151", msg);
            setReplicationStatus(thisNodeSession, pid, nodeId, ReplicationStatus.FAILED, failure);
            logMetacat.error(msg);
            throw new ServiceFailure("2151", msg);
        }

        // verify checksum on the object, if supported
        if (object.markSupported()) {
            Checksum givenChecksum = sysmeta.getChecksum();
            Checksum computedChecksum = null;
            try {
                computedChecksum = ChecksumUtil.checksum(object, givenChecksum.getAlgorithm());
                object.reset();

            } catch (Exception e) {
                String msg = "Error computing checksum on replica: " + e.getMessage();
                logMetacat.error(msg);
                ServiceFailure sf = new ServiceFailure("2151", msg);
                sf.initCause(e);
                setReplicationStatus(thisNodeSession, pid, nodeId, ReplicationStatus.FAILED, sf);
                throw sf;
            }
            if (!givenChecksum.getValue().equals(computedChecksum.getValue())) {
                logMetacat.error("Given    checksum for " + pid.getValue() + 
                    "is " + givenChecksum.getValue());
                logMetacat.error("Computed checksum for " + pid.getValue() + 
                    "is " + computedChecksum.getValue());
                String msg = "Computed checksum does not match declared checksum";
                failure = new ServiceFailure("2151", msg);
                setReplicationStatus(thisNodeSession, pid, nodeId, ReplicationStatus.FAILED, failure);
                throw new ServiceFailure("2151", msg);
            }
        }

        // add it to local store
        Identifier retPid;
        try {
            // skip the MN.create -- this mutates the system metadata and we don't want it to
            if ( localId == null ) {
                // TODO: this will fail if we already "know" about the identifier
            	// FIXME: see https://redmine.dataone.org/issues/2572
                retPid = super.create(session, pid, object, sysmeta);
                result = (retPid.getValue().equals(pid.getValue()));
            }
            
        } catch (Exception e) {
            String msg = "Could not save object to local store (" + e.getClass().getName() + "): " + e.getMessage();
            failure = new ServiceFailure("2151", msg);
            setReplicationStatus(thisNodeSession, pid, nodeId, ReplicationStatus.FAILED, failure);
            logMetacat.error(msg);
            throw new ServiceFailure("2151", msg);
            
        }

        // finish by setting the replication status
        setReplicationStatus(thisNodeSession, pid, nodeId, ReplicationStatus.COMPLETED, null);
        return result;

    }

    /**
     * Return the object identified by the given object identifier
     * 
     * @param session - the Session object containing the credentials for the Subject
     * @param pid - the object identifier for the given object
     * 
     * @return inputStream - the input stream of the given object
     * 
     * @throws InvalidToken
     * @throws ServiceFailure
     * @throws NotAuthorized
     * @throws InvalidRequest
     * @throws NotImplemented
     */
    @Override
    public InputStream get(Session session, Identifier pid) 
    throws InvalidToken, ServiceFailure, NotAuthorized, NotFound, NotImplemented {

        return super.get(session, pid);

    }

    /**
     * Returns a Checksum for the specified object using an accepted hashing algorithm
     * 
     * @param session - the Session object containing the credentials for the Subject
     * @param pid - the object identifier for the given object
     * @param algorithm -  the name of an algorithm that will be used to compute 
     *                     a checksum of the bytes of the object
     * 
     * @return checksum - the checksum of the given object
     * 
     * @throws InvalidToken
     * @throws ServiceFailure
     * @throws NotAuthorized
     * @throws NotFound
     * @throws InvalidRequest
     * @throws NotImplemented
     */
    @Override
    public Checksum getChecksum(Session session, Identifier pid, String algorithm) 
        throws InvalidToken, ServiceFailure, NotAuthorized, NotFound,
        InvalidRequest, NotImplemented {

        Checksum checksum = null;

        InputStream inputStream = get(session, pid);

        try {
            checksum = ChecksumUtil.checksum(inputStream, algorithm);

        } catch (NoSuchAlgorithmException e) {
            throw new ServiceFailure("1410", "The checksum for the object specified by " + pid.getValue() + "could not be returned due to an internal error: "
                    + e.getMessage());
        } catch (IOException e) {
            throw new ServiceFailure("1410", "The checksum for the object specified by " + pid.getValue() + "could not be returned due to an internal error: "
                    + e.getMessage());
        }

        if (checksum == null) {
            throw new ServiceFailure("1410", "The checksum for the object specified by " + pid.getValue() + "could not be returned.");
        }

        return checksum;
    }

    /**
     * Return the system metadata for a given object
     * 
     * @param session - the Session object containing the credentials for the Subject
     * @param pid - the object identifier for the given object
     * 
     * @return inputStream - the input stream of the given system metadata object
     * 
     * @throws InvalidToken
     * @throws ServiceFailure
     * @throws NotAuthorized
     * @throws NotFound
     * @throws InvalidRequest
     * @throws NotImplemented
     */
    @Override
    public SystemMetadata getSystemMetadata(Session session, Identifier pid) 
        throws InvalidToken, ServiceFailure, NotAuthorized, NotFound,
        NotImplemented {

        return super.getSystemMetadata(session, pid);
    }

    /**
     * Retrieve the list of objects present on the MN that match the calling parameters
     * 
     * @param session - the Session object containing the credentials for the Subject
     * @param startTime - Specifies the beginning of the time range from which 
     *                    to return object (>=)
     * @param endTime - Specifies the beginning of the time range from which 
     *                  to return object (>=)
     * @param objectFormat - Restrict results to the specified object format
     * @param replicaStatus - Indicates if replicated objects should be returned in the list
     * @param start - The zero-based index of the first value, relative to the 
     *                first record of the resultset that matches the parameters.
     * @param count - The maximum number of entries that should be returned in 
     *                the response. The Member Node may return less entries 
     *                than specified in this value.
     * 
     * @return objectList - the list of objects matching the criteria
     * 
     * @throws InvalidToken
     * @throws ServiceFailure
     * @throws NotAuthorized
     * @throws InvalidRequest
     * @throws NotImplemented
     */
    @Override
    public ObjectList listObjects(Session session, Date startTime, Date endTime, ObjectFormatIdentifier objectFormatId, Identifier identifier, Boolean replicaStatus, Integer start,
            Integer count) throws NotAuthorized, InvalidRequest, NotImplemented, ServiceFailure, InvalidToken {

        ObjectList objectList = null;

        try {
        	// safeguard against large requests
            if (count == null || count > MAXIMUM_DB_RECORD_COUNT) {
            	count = MAXIMUM_DB_RECORD_COUNT;
            }
            objectList = IdentifierManager.getInstance().querySystemMetadata(startTime, endTime, objectFormatId, replicaStatus, start, count);
        } catch (Exception e) {
            throw new ServiceFailure("1580", "Error querying system metadata: " + e.getMessage());
        }

        return objectList;
    }

    /**
     * Return a description of the node's capabilities and services.
     * 
     * @return node - the technical capabilities of the Member Node
     * 
     * @throws ServiceFailure
     * @throws NotAuthorized
     * @throws InvalidRequest
     * @throws NotImplemented
     */
    @Override
    public Node getCapabilities() 
        throws NotImplemented, ServiceFailure {

        String nodeName = null;
        String nodeId = null;
        String subject = null;
        String contactSubject = null;
        String nodeDesc = null;
        String nodeTypeString = null;
        NodeType nodeType = null;
        String mnCoreServiceVersion = null;
        String mnReadServiceVersion = null;
        String mnAuthorizationServiceVersion = null;
        String mnStorageServiceVersion = null;
        String mnReplicationServiceVersion = null;

        boolean nodeSynchronize = false;
        boolean nodeReplicate = false;
        boolean mnCoreServiceAvailable = false;
        boolean mnReadServiceAvailable = false;
        boolean mnAuthorizationServiceAvailable = false;
        boolean mnStorageServiceAvailable = false;
        boolean mnReplicationServiceAvailable = false;

        try {
            // get the properties of the node based on configuration information
            nodeName = PropertyService.getProperty("dataone.nodeName");
            nodeId = PropertyService.getProperty("dataone.nodeId");
            subject = PropertyService.getProperty("dataone.subject");
            contactSubject = PropertyService.getProperty("dataone.contactSubject");
            nodeDesc = PropertyService.getProperty("dataone.nodeDescription");
            nodeTypeString = PropertyService.getProperty("dataone.nodeType");
            nodeType = NodeType.convert(nodeTypeString);
            nodeSynchronize = new Boolean(PropertyService.getProperty("dataone.nodeSynchronize")).booleanValue();
            nodeReplicate = new Boolean(PropertyService.getProperty("dataone.nodeReplicate")).booleanValue();

            mnCoreServiceVersion = PropertyService.getProperty("dataone.mnCore.serviceVersion");
            mnReadServiceVersion = PropertyService.getProperty("dataone.mnRead.serviceVersion");
            mnAuthorizationServiceVersion = PropertyService.getProperty("dataone.mnAuthorization.serviceVersion");
            mnStorageServiceVersion = PropertyService.getProperty("dataone.mnStorage.serviceVersion");
            mnReplicationServiceVersion = PropertyService.getProperty("dataone.mnReplication.serviceVersion");

            mnCoreServiceAvailable = new Boolean(PropertyService.getProperty("dataone.mnCore.serviceAvailable")).booleanValue();
            mnReadServiceAvailable = new Boolean(PropertyService.getProperty("dataone.mnRead.serviceAvailable")).booleanValue();
            mnAuthorizationServiceAvailable = new Boolean(PropertyService.getProperty("dataone.mnAuthorization.serviceAvailable")).booleanValue();
            mnStorageServiceAvailable = new Boolean(PropertyService.getProperty("dataone.mnStorage.serviceAvailable")).booleanValue();
            mnReplicationServiceAvailable = new Boolean(PropertyService.getProperty("dataone.mnReplication.serviceAvailable")).booleanValue();

            // Set the properties of the node based on configuration information and
            // calls to current status methods
            String serviceName = SystemUtil.getSecureContextURL() + "/" + PropertyService.getProperty("dataone.serviceName");
            Node node = new Node();
            node.setBaseURL(serviceName + "/" + nodeTypeString);
            node.setDescription(nodeDesc);

            // set the node's health information
            node.setState(NodeState.UP);
            
            // set the ping response to the current value
            Ping canPing = new Ping();
            canPing.setSuccess(false);
            try {
            	Date pingDate = ping();
                canPing.setSuccess(pingDate != null);
            } catch (BaseException e) {
                e.printStackTrace();
                // guess it can't be pinged
            }
            
            node.setPing(canPing);

            NodeReference identifier = new NodeReference();
            identifier.setValue(nodeId);
            node.setIdentifier(identifier);
            Subject s = new Subject();
            s.setValue(subject);
            node.addSubject(s);
            Subject contact = new Subject();
            contact.setValue(contactSubject);
            node.addContactSubject(contact);
            node.setName(nodeName);
            node.setReplicate(nodeReplicate);
            node.setSynchronize(nodeSynchronize);

            // services: MNAuthorization, MNCore, MNRead, MNReplication, MNStorage
            Services services = new Services();

            Service sMNCore = new Service();
            sMNCore.setName("MNCore");
            sMNCore.setVersion(mnCoreServiceVersion);
            sMNCore.setAvailable(mnCoreServiceAvailable);

            Service sMNRead = new Service();
            sMNRead.setName("MNRead");
            sMNRead.setVersion(mnReadServiceVersion);
            sMNRead.setAvailable(mnReadServiceAvailable);

            Service sMNAuthorization = new Service();
            sMNAuthorization.setName("MNAuthorization");
            sMNAuthorization.setVersion(mnAuthorizationServiceVersion);
            sMNAuthorization.setAvailable(mnAuthorizationServiceAvailable);

            Service sMNStorage = new Service();
            sMNStorage.setName("MNStorage");
            sMNStorage.setVersion(mnStorageServiceVersion);
            sMNStorage.setAvailable(mnStorageServiceAvailable);

            Service sMNReplication = new Service();
            sMNReplication.setName("MNReplication");
            sMNReplication.setVersion(mnReplicationServiceVersion);
            sMNReplication.setAvailable(mnReplicationServiceAvailable);

            services.addService(sMNRead);
            services.addService(sMNCore);
            services.addService(sMNAuthorization);
            services.addService(sMNStorage);
            services.addService(sMNReplication);
            node.setServices(services);

            // Set the schedule for synchronization
            Synchronization synchronization = new Synchronization();
            Schedule schedule = new Schedule();
            Date now = new Date();
            schedule.setYear(PropertyService.getProperty("dataone.nodeSynchronization.schedule.year"));
            schedule.setMon(PropertyService.getProperty("dataone.nodeSynchronization.schedule.mon"));
            schedule.setMday(PropertyService.getProperty("dataone.nodeSynchronization.schedule.mday"));
            schedule.setWday(PropertyService.getProperty("dataone.nodeSynchronization.schedule.wday"));
            schedule.setHour(PropertyService.getProperty("dataone.nodeSynchronization.schedule.hour"));
            schedule.setMin(PropertyService.getProperty("dataone.nodeSynchronization.schedule.min"));
            schedule.setSec(PropertyService.getProperty("dataone.nodeSynchronization.schedule.sec"));
            synchronization.setSchedule(schedule);
            synchronization.setLastHarvested(now);
            synchronization.setLastCompleteHarvest(now);
            node.setSynchronization(synchronization);

            node.setType(nodeType);
            return node;

        } catch (PropertyNotFoundException pnfe) {
            String msg = "MNodeService.getCapabilities(): " + "property not found: " + pnfe.getMessage();
            logMetacat.error(msg);
            throw new ServiceFailure("2162", msg);
        }
    }

    

    /**
     * A callback method used by a CN to indicate to a MN that it cannot 
     * complete synchronization of the science metadata identified by pid.  Log
     * the event in the metacat event log.
     * 
     * @param session
     * @param syncFailed
     * 
     * @throws ServiceFailure
     * @throws NotAuthorized
     * @throws NotImplemented
     */
    @Override
    public boolean synchronizationFailed(Session session, SynchronizationFailed syncFailed) 
        throws NotImplemented, ServiceFailure, NotAuthorized {

        String localId;
        Identifier pid;
        if ( syncFailed.getPid() != null ) {
            pid = new Identifier();
            pid.setValue(syncFailed.getPid());
            boolean allowed;
            
            //are we allowed? only CNs
            try {
                allowed = isAdminAuthorized(session);
                if ( !allowed ){
                    throw new NotAuthorized("2162", 
                            "Not allowed to call synchronizationFailed() on this node.");
                }
            } catch (InvalidToken e) {
                throw new NotAuthorized("2162", 
                        "Not allowed to call synchronizationFailed() on this node.");

            }
            
        } else {
            throw new ServiceFailure("2161", "The identifier cannot be null.");

        }
        
        try {
            localId = IdentifierManager.getInstance().getLocalId(pid.getValue());
        } catch (McdbDocNotFoundException e) {
            throw new ServiceFailure("2161", "The identifier specified by " + 
                    syncFailed.getPid() + " was not found on this node.");

        }
        // TODO: update the CN URL below when the CNRead.SynchronizationFailed
        // method is changed to include the URL as a parameter
        logMetacat.debug("Synchronization for the object identified by " + 
                pid.getValue() + " failed from " + syncFailed.getNodeId() + 
                " Logging the event to the Metacat EventLog as a 'syncFailed' event.");
        // TODO: use the event type enum when the SYNCHRONIZATION_FAILED event is added
        String principal = Constants.SUBJECT_PUBLIC;
        if (session != null && session.getSubject() != null) {
          principal = session.getSubject().getValue();
        }
        try {
          EventLog.getInstance().log(request.getRemoteAddr(), request.getHeader("User-Agent"), principal, localId, "synchronization_failed");
        } catch (Exception e) {
            throw new ServiceFailure("2161", "Could not log the error for: " + pid.getValue());
        }
        //EventLog.getInstance().log("CN URL WILL GO HERE", 
        //  session.getSubject().getValue(), localId, Event.SYNCHRONIZATION_FAILED);
        return true;

    }

    /**
     * Essentially a get() but with different logging behavior
     */
    @Override
    public InputStream getReplica(Session session, Identifier pid) 
        throws NotAuthorized, NotImplemented, ServiceFailure, InvalidToken {

        logMetacat.info("MNodeService.getReplica() called.");

        // cannot be called by public
        if (session == null) {
        	throw new InvalidToken("2183", "No session was provided.");
        }
        
        logMetacat.info("MNodeService.getReplica() called with parameters: \n" +
             "\tSession.Subject      = " + session.getSubject().getValue() + "\n" +
             "\tIdentifier           = " + pid.getValue());

        InputStream inputStream = null; // bytes to be returned
        handler = new MetacatHandler(new Timer());
        boolean allowed = false;
        String localId; // the metacat docid for the pid

        // get the local docid from Metacat
        try {
            localId = IdentifierManager.getInstance().getLocalId(pid.getValue());
        } catch (McdbDocNotFoundException e) {
            throw new ServiceFailure("2181", "The object specified by " + 
                    pid.getValue() + " does not exist at this node.");
            
        }

        Subject targetNodeSubject = session.getSubject();

        // check for authorization to replicate, null session to act as this source MN
        try {
            allowed = D1Client.getCN().isNodeAuthorized(null, targetNodeSubject, pid);
        } catch (InvalidToken e1) {
            throw new ServiceFailure("2181", "Could not determine if node is authorized: " 
                + e1.getMessage());
            
        } catch (NotFound e1) {
            throw new ServiceFailure("2181", "Could not determine if node is authorized: " 
                    + e1.getMessage());

        } catch (InvalidRequest e1) {
            throw new ServiceFailure("2181", "Could not determine if node is authorized: " 
                    + e1.getMessage());

        }

        logMetacat.info("Called D1Client.isNodeAuthorized(). Allowed = " + allowed +
            " for identifier " + pid.getValue());

        // if the person is authorized, perform the read
        if (allowed) {
            try {
                inputStream = MetacatHandler.read(localId);
            } catch (Exception e) {
                throw new ServiceFailure("1020", "The object specified by " + 
                    pid.getValue() + "could not be returned due to error: " + e.getMessage());
            }
        }

        // if we fail to set the input stream
        if (inputStream == null) {
            throw new ServiceFailure("2181", "The object specified by " + 
                pid.getValue() + "does not exist at this node.");
        }

        // log the replica event
        String principal = null;
        if (session.getSubject() != null) {
            principal = session.getSubject().getValue();
        }
        EventLog.getInstance().log(request.getRemoteAddr(), 
            request.getHeader("User-Agent"), principal, localId, "replicate");

        return inputStream;
    }

    /**
     * A method to notify the Member Node that the authoritative copy of 
     * system metadata on the Coordinating Nodes has changed.
     * 
     * @param session   Session information that contains the identity of the 
     *                  calling user as retrieved from the X.509 certificate 
     *                  which must be traceable to the CILogon service.
     * @param serialVersion   The serialVersion of the system metadata
     * @param dateSysMetaLastModified  The time stamp for when the system metadata was changed
     * @throws NotImplemented
     * @throws ServiceFailure
     * @throws NotAuthorized
     * @throws InvalidRequest
     * @throws InvalidToken
     */
    public boolean systemMetadataChanged(Session session, Identifier pid,
        long serialVersion, Date dateSysMetaLastModified) 
        throws NotImplemented, ServiceFailure, NotAuthorized, InvalidRequest,
        InvalidToken {
        
        // cannot be called by public
        if (session == null) {
        	throw new InvalidToken("2183", "No session was provided.");
        }

        SystemMetadata currentLocalSysMeta = null;
        SystemMetadata newSysMeta = null;
        CNode cn = D1Client.getCN();
        NodeList nodeList = null;
        Subject callingSubject = null;
        boolean allowed = false;
        
        // are we allowed to call this?
        callingSubject = session.getSubject();
        nodeList = cn.listNodes();
        
        for(Node node : nodeList.getNodeList()) {
            // must be a CN
            if ( node.getType().equals(NodeType.CN)) {
               List<Subject> subjectList = node.getSubjectList();
               // the calling subject must be in the subject list
               if ( subjectList.contains(callingSubject)) {
                   allowed = true;
                   
               }
               
            }
        }
        
        if (!allowed ) {
            String msg = "The subject identified by " + callingSubject.getValue() +
              " is not authorized to call this service.";
            throw new NotAuthorized("1331", msg);
            
        }
        
        // compare what we have locally to what is sent in the change notification
        try {
            currentLocalSysMeta = HazelcastService.getInstance().getSystemMetadataMap().get(pid);
             
        } catch (RuntimeException e) {
            String msg = "SystemMetadata for pid " + pid.getValue() +
              " couldn't be updated because it couldn't be found locally: " +
              e.getMessage();
            logMetacat.error(msg);
            ServiceFailure sf = new ServiceFailure("1333", msg);
            sf.initCause(e);
            throw sf; 
        }
        
        if (currentLocalSysMeta.getSerialVersion().longValue() < serialVersion ) {
            try {
                newSysMeta = cn.getSystemMetadata(null, pid);
            } catch (NotFound e) {
                // huh? you just said you had it
            	String msg = "On updating the local copy of system metadata " + 
                "for pid " + pid.getValue() +", the CN reports it is not found." +
                " The error message was: " + e.getMessage();
                logMetacat.error(msg);
                ServiceFailure sf = new ServiceFailure("1333", msg);
                sf.initCause(e);
                throw sf;
            }
            
            // update the local copy of system metadata for the pid
            try {
                HazelcastService.getInstance().getSystemMetadataMap().put(newSysMeta.getIdentifier(), newSysMeta);
                logMetacat.info("Updated local copy of system metadata for pid " +
                    pid.getValue() + " after change notification from the CN.");
                
                // TODO: consider inspecting the change for archive
                // see: https://projects.ecoinformatics.org/ecoinfo/issues/6417
//                if (newSysMeta.getArchived() != null && newSysMeta.getArchived().booleanValue()) {
//                	try {
//						this.archive(session, newSysMeta.getIdentifier());
//					} catch (NotFound e) {
//						// do we care? nothing to do about it now
//						logMetacat.error(e.getMessage(), e);
//					}
//                }
                
            } catch (RuntimeException e) {
                String msg = "SystemMetadata for pid " + pid.getValue() +
                  " couldn't be updated: " +
                  e.getMessage();
                logMetacat.error(msg);
                ServiceFailure sf = new ServiceFailure("1333", msg);
                sf.initCause(e);
                throw sf;
            }
            
            // submit for indexing
            try {
				MetacatSolrIndex.getInstance().submit(newSysMeta.getIdentifier(), newSysMeta, null, true);
			} catch (Exception e) {
                logMetacat.error("Could not submit changed systemMetadata for indexing, pid: " + newSysMeta.getIdentifier().getValue(), e);
			}
        }
        
        return true;
        
    }
    
    /*
     * Set the replication status for the object on the Coordinating Node
     * 
     * @param session - the session for the this target node
     * @param pid - the identifier of the object being updated
     * @param nodeId - the identifier of this target node
     * @param status - the replication status to set
     * @param failure - the exception to include, if any
     */
    private void setReplicationStatus(Session session, Identifier pid, 
        NodeReference nodeId, ReplicationStatus status, BaseException failure) 
        throws ServiceFailure, NotImplemented, NotAuthorized, 
        InvalidRequest {
        
        // call the CN as the MN to set the replication status
        try {
            this.cn = D1Client.getCN();
            this.cn.setReplicationStatus(session, pid, nodeId,
                    status, failure);
            
        } catch (InvalidToken e) {
        	String msg = "Could not set the replication status for " + pid.getValue() + " on the CN (InvalidToken): " + e.getMessage();
            logMetacat.error(msg);
        	throw new ServiceFailure("2151",
                    msg);
            
        } catch (NotFound e) {
        	String msg = "Could not set the replication status for " + pid.getValue() + " on the CN (NotFound): " + e.getMessage();
            logMetacat.error(msg);
        	throw new ServiceFailure("2151",
                    msg);
            
        }
    }
    
    private SystemMetadata makePublicIfNot(SystemMetadata sysmeta, Identifier pid) throws ServiceFailure, InvalidToken, NotFound, NotImplemented, InvalidRequest {
    	// check if it is publicly readable
		boolean isPublic = false;
		Subject publicSubject = new Subject();
		publicSubject.setValue(Constants.SUBJECT_PUBLIC);
		Session publicSession = new Session();
		publicSession.setSubject(publicSubject);
		AccessRule publicRule = new AccessRule();
		publicRule.addPermission(Permission.READ);
		publicRule.addSubject(publicSubject);
		
		// see if we need to add the rule
		try {
			isPublic = this.isAuthorized(publicSession, pid, Permission.READ);
		} catch (NotAuthorized na) {
			// well, certainly not authorized for public read!
		}
		if (!isPublic) {
			sysmeta.getAccessPolicy().addAllow(publicRule);
		}
		
		return sysmeta;
    }

	@Override
	public Identifier generateIdentifier(Session session, String scheme, String fragment)
			throws InvalidToken, ServiceFailure, NotAuthorized, NotImplemented,
			InvalidRequest {
		
		// check for null session
        if (session == null) {
          throw new InvalidToken("2190", "Session is required to generate an Identifier at this Node.");
        }
		
		Identifier identifier = new Identifier();
		
		// handle different schemes
		if (scheme.equalsIgnoreCase(UUID_SCHEME)) {
			// UUID
			UUID uuid = UUID.randomUUID();
            identifier.setValue(UUID_PREFIX + uuid.toString());
		} else if (scheme.equalsIgnoreCase(DOI_SCHEME)) {
			// generate a DOI
			try {
				identifier = DOIService.getInstance().generateDOI();
			} catch (EZIDException e) {
				ServiceFailure sf = new ServiceFailure("2191", "Could not generate DOI: " + e.getMessage());
				sf.initCause(e);
				throw sf;
			}
		} else {
			// default if we don't know the scheme
			if (fragment != null) {
				// for now, just autogen with fragment
				String autogenId = DocumentUtil.generateDocumentId(fragment, 0);
				identifier.setValue(autogenId);			
			} else {
				// autogen with no fragment
				String autogenId = DocumentUtil.generateDocumentId(0);
				identifier.setValue(autogenId);
			}
		}
		
		// TODO: reserve the identifier with the CN. We can only do this when
		// 1) the MN is part of a CN cluster
		// 2) the request is from an authenticated user
		
		return identifier;
	}

	

	@Override
	public QueryEngineDescription getQueryEngineDescription(Session session, String engine)
			throws InvalidToken, ServiceFailure, NotAuthorized, NotImplemented,
			NotFound {
	    if(engine != null && engine.equals(EnabledQueryEngines.PATHQUERYENGINE)) {
	        if(!EnabledQueryEngines.getInstance().isEnabled(EnabledQueryEngines.PATHQUERYENGINE)) {
                throw new NotImplemented("0000", "MNodeService.query - the query engine "+engine +" hasn't been implemented or has been disabled.");
            }
	        QueryEngineDescription qed = new QueryEngineDescription();
	        qed.setName(EnabledQueryEngines.PATHQUERYENGINE);
	        qed.setQueryEngineVersion("1.0");
	        qed.addAdditionalInfo("This is the traditional structured query for Metacat");
	        Vector<String> pathsForIndexing = null;
	        try {
	            pathsForIndexing = SystemUtil.getPathsForIndexing();
	        } catch (MetacatUtilException e) {
	            logMetacat.warn("Could not get index paths", e);
	        }
	        for (String fieldName: pathsForIndexing) {
	            QueryField field = new QueryField();
	            field.addDescription("Indexed field for path '" + fieldName + "'");
	            field.setName(fieldName);
	            field.setReturnable(true);
	            field.setSearchable(true);
	            field.setSortable(false);
	            // TODO: determine type and multivaluedness
	            field.setType(String.class.getName());
	            //field.setMultivalued(true);
	            qed.addQueryField(field);
	        }
	        return qed;
	    } else if (engine != null && engine.equals(EnabledQueryEngines.SOLRENGINE)) {
	        if(!EnabledQueryEngines.getInstance().isEnabled(EnabledQueryEngines.SOLRENGINE)) {
                throw new NotImplemented("0000", "MNodeService.getQueryEngineDescription - the query engine "+engine +" hasn't been implemented or has been disabled.");
            }
	        try {
	            QueryEngineDescription qed = MetacatSolrEngineDescriptionHandler.getInstance().getQueryEngineDescritpion();
	            return qed;
	        } catch (Exception e) {
	            e.printStackTrace();
	            throw new ServiceFailure("Solr server error", e.getMessage());
	        }
	    } else {
	        throw new NotFound("404", "The Metacat member node can't find the query engine - "+engine);
	    }
		
	}

	@Override
	public QueryEngineList listQueryEngines(Session session) throws InvalidToken,
			ServiceFailure, NotAuthorized, NotImplemented {
		QueryEngineList qel = new QueryEngineList();
		//qel.addQueryEngine(EnabledQueryEngines.PATHQUERYENGINE);
		//qel.addQueryEngine(EnabledQueryEngines.SOLRENGINE);
		List<String> enables = EnabledQueryEngines.getInstance().getEnabled();
		for(String name : enables) {
		    qel.addQueryEngine(name);
		}
		return qel;
	}

	@Override
	public InputStream query(Session session, String engine, String query) throws InvalidToken,
			ServiceFailure, NotAuthorized, InvalidRequest, NotImplemented,
			NotFound {
	    String user = Constants.SUBJECT_PUBLIC;
        String[] groups= null;
        Set<Subject> subjects = null;
        if (session != null) {
            user = session.getSubject().getValue();
            subjects = AuthUtils.authorizedClientSubjects(session);
            if (subjects != null) {
                List<String> groupList = new ArrayList<String>();
                for (Subject subject: subjects) {
                    groupList.add(subject.getValue());
                }
                groups = groupList.toArray(new String[0]);
            }
        } else {
            //add the public user subject to the set 
            Subject subject = new Subject();
            subject.setValue(Constants.SUBJECT_PUBLIC);
            subjects = new HashSet<Subject>();
            subjects.add(subject);
        }
        //System.out.println("====== user is "+user);
        //System.out.println("====== groups are "+groups);
		if (engine != null && engine.equals(EnabledQueryEngines.PATHQUERYENGINE)) {
		    if(!EnabledQueryEngines.getInstance().isEnabled(EnabledQueryEngines.PATHQUERYENGINE)) {
                throw new NotImplemented("0000", "MNodeService.query - the query engine "+engine +" hasn't been implemented or has been disabled.");
            }
			try {
				DBQuery queryobj = new DBQuery();
				
				String results = queryobj.performPathquery(query, user, groups);
				ContentTypeByteArrayInputStream ctbais = new ContentTypeByteArrayInputStream(results.getBytes(MetaCatServlet.DEFAULT_ENCODING));
				ctbais.setContentType("text/xml");
				return ctbais;

			} catch (Exception e) {
				throw new ServiceFailure("Pathquery error", e.getMessage());
			}
			
		} else if (engine != null && engine.equals(EnabledQueryEngines.SOLRENGINE)) {
		    if(!EnabledQueryEngines.getInstance().isEnabled(EnabledQueryEngines.SOLRENGINE)) {
		        throw new NotImplemented("0000", "MNodeService.query - the query engine "+engine +" hasn't been implemented or has been disabled.");
		    }
		    logMetacat.info("The query is ==================================== \n"+query);
		    try {
		        
                return MetacatSolrIndex.getInstance().query(query, subjects);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                throw new ServiceFailure("Solr server error", e.getMessage());
            } 
		}
		return null;
	}
	
	/**
	 * Given an existing Science Metadata PID, this method mints a DOI
	 * and updates the original object "publishing" the update with the DOI.
	 * This includes updating the ORE map that describes the Science Metadata+data.
	 * TODO: ensure all referenced objects allow public read
	 * 
	 * @see https://projects.ecoinformatics.org/ecoinfo/issues/6014
	 * 
	 * @param originalIdentifier
	 * @param request
	 * @throws InvalidRequest 
	 * @throws NotImplemented 
	 * @throws NotAuthorized 
	 * @throws ServiceFailure 
	 * @throws InvalidToken 
	 * @throws NotFound
	 * @throws InvalidSystemMetadata 
	 * @throws InsufficientResources 
	 * @throws UnsupportedType 
	 * @throws IdentifierNotUnique 
	 */
	public Identifier publish(Session session, Identifier originalIdentifier) throws InvalidToken, ServiceFailure, NotAuthorized, NotImplemented, InvalidRequest, NotFound, IdentifierNotUnique, UnsupportedType, InsufficientResources, InvalidSystemMetadata {
		
		
		// get the original SM
		SystemMetadata originalSystemMetadata = this.getSystemMetadata(session, originalIdentifier);

		// make copy of it using the marshaller to ensure DEEP copy
		SystemMetadata sysmeta = null;
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			TypeMarshaller.marshalTypeToOutputStream(originalSystemMetadata, baos);
			sysmeta = TypeMarshaller.unmarshalTypeFromStream(SystemMetadata.class, new ByteArrayInputStream(baos.toByteArray()));
		} catch (Exception e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		}

		// mint a DOI for the new revision
		Identifier newIdentifier = this.generateIdentifier(session, MNodeService.DOI_SCHEME, null);
				
		// set new metadata values
		sysmeta.setIdentifier(newIdentifier);
		sysmeta.setObsoletes(originalIdentifier);
		sysmeta.setObsoletedBy(null);
		
		// ensure it is publicly readable
		sysmeta = makePublicIfNot(sysmeta, originalIdentifier);
		
		// get the bytes
		InputStream inputStream = this.get(session, originalIdentifier);
		
		// update the object
		this.update(session, originalIdentifier, inputStream, newIdentifier, sysmeta);
		
		// update ORE that references the scimeta
		// first try the naive method, then check the SOLR index
		try {
			String localId = IdentifierManager.getInstance().getLocalId(originalIdentifier.getValue());
			
			Identifier potentialOreIdentifier = new Identifier();
			potentialOreIdentifier.setValue(SystemMetadataFactory.RESOURCE_MAP_PREFIX + localId);
			
			InputStream oreInputStream = null;
			try {
				oreInputStream = this.get(session, potentialOreIdentifier);
			} catch (NotFound nf) {
				// this is probably okay for many sci meta data docs
				logMetacat.warn("No potential ORE map found for: " + potentialOreIdentifier.getValue());
				// try the SOLR index
				List<Identifier> potentialOreIdentifiers = this.lookupOreFor(originalIdentifier, false);
				if (potentialOreIdentifiers != null) {
					potentialOreIdentifier = potentialOreIdentifiers.get(0);
					try {
						oreInputStream = this.get(session, potentialOreIdentifier);
					} catch (NotFound nf2) {
						// this is probably okay for many sci meta data docs
						logMetacat.warn("No potential ORE map found for: " + potentialOreIdentifier.getValue());
					}
				}
			}
			if (oreInputStream != null) {
				Identifier newOreIdentifier = MNodeService.getInstance(request).generateIdentifier(session, MNodeService.UUID_SCHEME, null);
	
				Map<Identifier, Map<Identifier, List<Identifier>>> resourceMapStructure = ResourceMapFactory.getInstance().parseResourceMap(oreInputStream);
				Map<Identifier, List<Identifier>> sciMetaMap = resourceMapStructure.get(potentialOreIdentifier);
				List<Identifier> dataIdentifiers = sciMetaMap.get(originalIdentifier);
					
				// reconstruct the ORE with the new identifiers
				sciMetaMap.remove(originalIdentifier);
				sciMetaMap.put(newIdentifier, dataIdentifiers);
				
				ResourceMap resourceMap = ResourceMapFactory.getInstance().createResourceMap(newOreIdentifier, sciMetaMap);
				String resourceMapString = ResourceMapFactory.getInstance().serializeResourceMap(resourceMap);
				
				// get the original ORE SM and update the values
				SystemMetadata originalOreSysMeta = this.getSystemMetadata(session, potentialOreIdentifier);
				SystemMetadata oreSysMeta = new SystemMetadata();
				try {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					TypeMarshaller.marshalTypeToOutputStream(originalOreSysMeta, baos);
					oreSysMeta = TypeMarshaller.unmarshalTypeFromStream(SystemMetadata.class, new ByteArrayInputStream(baos.toByteArray()));
				} catch (Exception e) {
					// report as service failure
					ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
					sf.initCause(e);
					throw sf;
				}

				oreSysMeta.setIdentifier(newOreIdentifier);
				oreSysMeta.setObsoletes(potentialOreIdentifier);
				oreSysMeta.setObsoletedBy(null);
				oreSysMeta.setSize(BigInteger.valueOf(resourceMapString.getBytes("UTF-8").length));
				oreSysMeta.setChecksum(ChecksumUtil.checksum(resourceMapString.getBytes("UTF-8"), oreSysMeta.getChecksum().getAlgorithm()));
				
				// ensure ORE is publicly readable
				oreSysMeta = makePublicIfNot(oreSysMeta, potentialOreIdentifier);
				
				// ensure all data objects allow public read
				List<String> pidsToSync = new ArrayList<String>();
				for (Identifier dataId: dataIdentifiers) {
					SystemMetadata dataSysMeta = this.getSystemMetadata(session, dataId);
					dataSysMeta = makePublicIfNot(dataSysMeta, dataId);
					this.updateSystemMetadata(dataSysMeta);
					pidsToSync.add(dataId.getValue());
				}
				SyncAccessPolicy sap = new SyncAccessPolicy();
				try {
					sap.sync(pidsToSync);
				} catch (Exception e) {
					// ignore
					logMetacat.warn("Error attempting to sync access for data objects when publishing package");
				}
				
				// save the updated ORE
				this.update(
						session, 
						potentialOreIdentifier, 
						new ByteArrayInputStream(resourceMapString.getBytes("UTF-8")), 
						newOreIdentifier, 
						oreSysMeta);
				
			} else {
				// create a new ORE for them
				// https://projects.ecoinformatics.org/ecoinfo/issues/6194
				try {
					// find the local id for the NEW package.
					String newLocalId = IdentifierManager.getInstance().getLocalId(newIdentifier.getValue());
	
					@SuppressWarnings("unused")
					SystemMetadata extraSysMeta = SystemMetadataFactory.createSystemMetadata(newLocalId, true, false);
					// should be done generating the ORE here, and the same permissions were used from the metadata object
					
				} catch (Exception e) {
					// oops, guess there was a problem - no package for you
					logMetacat.error("Could not generate new ORE for published object: " + newIdentifier.getValue(), e);
				}
			}
		} catch (McdbDocNotFoundException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		} catch (UnsupportedEncodingException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		} catch (OREException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		} catch (URISyntaxException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		} catch (OREParserException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		} catch (ORESerialiserException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		} catch (NoSuchAlgorithmException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		}
		
		return newIdentifier;
	}
	
	/**
	 * Determines if we already have registered an ORE map for this package
	 * NOTE: uses a solr query to locate OREs for the object
	 * @param guid of the EML/packaging object
	 * @return list of resource map identifiers for the given pid
	 */
	public List<Identifier> lookupOreFor(Identifier guid, boolean includeObsolete) {
		// Search for the ORE if we can find it
		String pid = guid.getValue();
		List<Identifier> retList = null;
		try {
			String query = "fl=id,resourceMap&wt=xml&q=-obsoletedBy:[* TO *]+resourceMap:[* TO *]+id:\"" + pid + "\"";
			if (includeObsolete) {
				query = "fl=id,resourceMap&wt=xml&q=resourceMap:[* TO *]+id:\"" + pid + "\"";
			}
			
			InputStream results = this.query(null, "solr", query);
			org.w3c.dom.Node rootNode = XMLUtilities.getXMLReaderAsDOMTreeRootNode(new InputStreamReader(results, "UTF-8"));
			//String resultString = XMLUtilities.getDOMTreeAsString(rootNode);
			org.w3c.dom.NodeList nodeList = XMLUtilities.getNodeListWithXPath(rootNode, "//arr[@name=\"resourceMap\"]/str");
			if (nodeList != null && nodeList.getLength() > 0) {
				retList = new ArrayList<Identifier>();
				for (int i = 0; i < nodeList.getLength(); i++) {
					String found = nodeList.item(i).getFirstChild().getNodeValue();
					Identifier oreId = new Identifier();
					oreId.setValue(found);
					retList.add(oreId);
				}
			}
		} catch (Exception e) {
			logMetacat.error("Error checking for resourceMap[s] on pid " + pid + ". " + e.getMessage(), e);
		}
		
		return retList;
	}
	

	@Override
	public InputStream getPackage(Session session, ObjectFormatIdentifier formatId,
			Identifier pid) throws InvalidToken, ServiceFailure,
			NotAuthorized, InvalidRequest, NotImplemented, NotFound {
		InputStream bagInputStream = null;
		BagFactory bagFactory = new BagFactory();
		Bag bag = bagFactory.createBag();
		
		// track the temp files we use so we can delete them when finished
		List<File> tempFiles = new ArrayList<File>();
		
		// the pids to include in the package
		List<Identifier> packagePids = new ArrayList<Identifier>();
		
		// catch non-D1 service errors and throw as ServiceFailures
		try {
			//Create a map of dataone ids and file names
			Map<Identifier, String> fileNames = new HashMap<Identifier, String>();
			
			// track the pid-to-file mapping
			StringBuffer pidMapping = new StringBuffer();
			
			// find the package contents
			SystemMetadata sysMeta = this.getSystemMetadata(session, pid);
			if (ObjectFormatCache.getInstance().getFormat(sysMeta.getFormatId()).getFormatType().equals("RESOURCE")) {
				//Get the resource map as a map of Identifiers
				InputStream oreInputStream = this.get(session, pid);
				Map<Identifier, Map<Identifier, List<Identifier>>> resourceMapStructure = ResourceMapFactory.getInstance().parseResourceMap(oreInputStream);
				packagePids.addAll(resourceMapStructure.keySet());
				//Loop through each object in this resource map
				for (Map<Identifier, List<Identifier>> entries: resourceMapStructure.values()) {
					//Loop through each metadata object in this entry
					Set<Identifier> metadataIdentifiers = entries.keySet();
					for(Identifier metadataID: metadataIdentifiers){
						try{
							//Get the system metadata for this metadata object
							SystemMetadata metadataSysMeta = this.getSystemMetadata(session, metadataID);
							
							// include user-friendly metadata
							if (ObjectFormatCache.getInstance().getFormat(metadataSysMeta.getFormatId()).getFormatType().equals("METADATA")) {
								InputStream metadataStream = this.get(session, metadataID);
							
								try {
									// transform
						            String format = "default";

									DBTransform transformer = new DBTransform();
						            String documentContent = IOUtils.toString(metadataStream, "UTF-8");
						            String sourceType = metadataSysMeta.getFormatId().getValue();
						            String targetType = "-//W3C//HTML//EN";
						            ByteArrayOutputStream baos = new ByteArrayOutputStream();
						            Writer writer = new OutputStreamWriter(baos , "UTF-8");
						            // TODO: include more params?
						            Hashtable<String, String[]> params = new Hashtable<String, String[]>();
						            String localId = null;
									try {
										localId = IdentifierManager.getInstance().getLocalId(pid.getValue());
									} catch (McdbDocNotFoundException e) {
										throw new NotFound("1020", e.getMessage());
									}
									params.put("qformat", new String[] {format});	            
						            params.put("docid", new String[] {localId});
						            params.put("pid", new String[] {pid.getValue()});
						            params.put("displaymodule", new String[] {"printall"});
						            
						            transformer.transformXMLDocument(
						                    documentContent , 
						                    sourceType, 
						                    targetType , 
						                    format, 
						                    writer, 
						                    params, 
						                    null //sessionid
						                    );
						            
						            // finally, get the HTML back
						            ContentTypeByteArrayInputStream resultInputStream = new ContentTypeByteArrayInputStream(baos.toByteArray());
						            
						            // write to temp file with correct css path
						            File tmpDir = File.createTempFile("package_", "_dir");
						            tmpDir.delete();
						            tmpDir.mkdir();
						            File htmlFile = File.createTempFile("metadata", ".html", tmpDir);
						            File cssDir = new File(tmpDir, format);
						            cssDir.mkdir();
						            File cssFile = new File(tmpDir, format + "/" + format + ".css");
						            String pdfFileName = metadataID.getValue().replaceAll("[^a-zA-Z0-9\\-\\.]", "_") + "-METADATA.pdf";
						            File pdfFile = new File(tmpDir, pdfFileName);
						            //File pdfFile = File.createTempFile("metadata", ".pdf", tmpDir);
						            
						            // put the CSS file in place for the html to find it
						            String originalCssPath = SystemUtil.getContextDir() + "/style/skins/" + format + "/" + format + ".css";
						            IOUtils.copy(new FileInputStream(originalCssPath), new FileOutputStream(cssFile));
						            
						            // write the HTML file
						            IOUtils.copy(resultInputStream, new FileOutputStream(htmlFile));
						            
						            // convert to PDF
						            HtmlToPdf.export(htmlFile.getAbsolutePath(), pdfFile.getAbsolutePath());
						            
						            //add to the package
						            bag.addFileToPayload(pdfFile);
									pidMapping.append(metadataID.getValue() + " (pdf)" +  "\t" + "data/" + pdfFile.getName() + "\n");
						            
						            // mark for clean up after we are done
									htmlFile.delete();
									cssFile.delete();
									cssDir.delete();
						            tempFiles.add(tmpDir);
									tempFiles.add(pdfFile); // delete this first later on
						            
								} catch (Exception e) {
									logMetacat.warn("Could not transform metadata", e);
								}
							}

							
							//If this is in eml format, extract the filename and GUID from each entity in its package
							if (metadataSysMeta.getFormatId().getValue().startsWith("eml://")) {
								//Get the package
								DataPackageParserInterface parser = new Eml200DataPackageParser();
								InputStream emlStream = this.get(session, metadataID);
								parser.parse(emlStream);
								DataPackage dataPackage = parser.getDataPackage();
								
								//Get all the entities in this package and loop through each to extract its ID and file name
								Entity[] entities = dataPackage.getEntityList();
								for(Entity entity: entities){
									try{
										//Get the file name from the metadata
										String fileNameFromMetadata = entity.getName();
										
										//Get the ecogrid URL from the metadata
										String ecogridIdentifier = entity.getEntityIdentifier();
										//Parse the ecogrid URL to get the local id
										String idFromMetadata = DocumentUtil.getAccessionNumberFromEcogridIdentifier(ecogridIdentifier);
										
										//Get the docid and rev pair
										String docid = DocumentUtil.getDocIdFromString(idFromMetadata);
										String rev = DocumentUtil.getRevisionStringFromString(idFromMetadata);
										
										//Get the GUID
										String guid = IdentifierManager.getInstance().getGUID(docid, Integer.valueOf(rev));
										Identifier dataIdentifier = new Identifier();
										dataIdentifier.setValue(guid);
										
										//Add the GUID to our GUID & file name map
										fileNames.put(dataIdentifier, fileNameFromMetadata);
									}
									catch(Exception e){
										//Prevent just one entity error
										e.printStackTrace();
										logMetacat.debug(e.getMessage(), e);
									}
								}
							}
						}
						catch(Exception e){
							//Catch errors that would prevent package download
							logMetacat.debug(e.toString());
						}
					}
					packagePids.addAll(entries.keySet());
					for (List<Identifier> dataPids: entries.values()) {
						packagePids.addAll(dataPids);
					}
				}
			} else {
				// just the lone pid in this package
				packagePids.add(pid);
			}
			
			//Create a temp file, then delete it and make a directory with that name
			File tempDir = File.createTempFile("temp", Long.toString(System.nanoTime()));
			tempDir.delete();
			tempDir = new File(tempDir.getPath() + "_dir");
			tempDir.mkdir();			
			tempFiles.add(tempDir);
			File pidMappingFile = new File(tempDir, "pid-mapping.txt");
			
			// loop through the package contents
			for (Identifier entryPid: packagePids) {
				//Get the system metadata for each item
				SystemMetadata entrySysMeta = this.getSystemMetadata(session, entryPid);					
				
				String objectFormatType = ObjectFormatCache.getInstance().getFormat(entrySysMeta.getFormatId()).getFormatType();
				String fileName = null;
				
				//TODO: Be more specific of what characters to replace. Make sure periods arent replaced for the filename from metadata
				//Our default file name is just the ID + format type (e.g. walker.1.1-DATA)
				fileName = entryPid.getValue().replaceAll("[^a-zA-Z0-9\\-\\.]", "_") + "-" + objectFormatType;

				if(fileNames.containsKey(entryPid)){
					//Let's use the file name and extension from the metadata is we have it
					fileName = entryPid.getValue().replaceAll("[^a-zA-Z0-9\\-\\.]", "_") + "-" + fileNames.get(entryPid).replaceAll("[^a-zA-Z0-9\\-\\.]", "_");
				}
				else{
					//If we couldn't find a given file name, use the system metadata extension
					String extension = ObjectFormatInfo.instance().getExtension(entrySysMeta.getFormatId().getValue());
					fileName += extension;
				}
				
		        //Create a new file for this item and add to the list
				File tempFile = new File(tempDir, fileName);
				tempFiles.add(tempFile);
				
				InputStream entryInputStream = this.get(session, entryPid);			
				IOUtils.copy(entryInputStream, new FileOutputStream(tempFile));
				bag.addFileToPayload(tempFile);
				pidMapping.append(entryPid.getValue() + "\t" + "data/" + tempFile.getName() + "\n");
			}
			
			//add the the pid to data file map
			IOUtils.write(pidMapping.toString(), new FileOutputStream(pidMappingFile));
			bag.addFileAsTag(pidMappingFile);
			tempFiles.add(pidMappingFile);
			
			bag = bag.makeComplete();
			
			///Now create the zip file
			//Use the pid as the file name prefix, replacing all non-word characters
			String zipName = pid.getValue().replaceAll("\\W", "_");
			
			File bagFile = new File(tempDir, zipName+".zip");
			
			bag.setFile(bagFile);
			ZipWriter zipWriter = new ZipWriter(bagFactory);
			bag.write(zipWriter, bagFile);
			bagFile = bag.getFile();
			// use custom FIS that will delete the file when closed
			bagInputStream = new DeleteOnCloseFileInputStream(bagFile);
			// also mark for deletion on shutdown in case the stream is never closed
			bagFile.deleteOnExit();
			tempFiles.add(bagFile);
			
			// clean up other temp files
			for (int i=tempFiles.size()-1; i>=0; i--){
				tempFiles.get(i).delete();
			}
			
		} catch (IOException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		} catch (OREException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		} catch (URISyntaxException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		} catch (OREParserException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		}
		
		return bagInputStream;
	}

	@Override
	public OptionList listViews(Session arg0) throws InvalidToken,
			ServiceFailure, NotAuthorized, InvalidRequest, NotImplemented {
		OptionList views = new OptionList();
		Vector<String> skinNames = null;
		try {
			skinNames = SkinUtil.getSkinNames();
		} catch (PropertyNotFoundException e) {
			throw new ServiceFailure("2841", e.getMessage());
		}
		for (String skinName: skinNames) {
			views.addOption(skinName);
		}
		return views;
	}

	@Override
	public InputStream view(Session session, String format, Identifier pid)
			throws InvalidToken, ServiceFailure, NotAuthorized, InvalidRequest,
			NotImplemented, NotFound {
		InputStream resultInputStream = null;
		
		SystemMetadata sysMeta = this.getSystemMetadata(session, pid);
		InputStream object = this.get(session, pid);

		try {
			// can only transform metadata, really
			ObjectFormat objectFormat = ObjectFormatCache.getInstance().getFormat(sysMeta.getFormatId());
			if (objectFormat.getFormatType().equals("METADATA")) {
				// transform
				DBTransform transformer = new DBTransform();
	            String documentContent = IOUtils.toString(object, "UTF-8");
	            String sourceType = objectFormat.getFormatId().getValue();
	            String targetType = "-//W3C//HTML//EN";
	            ByteArrayOutputStream baos = new ByteArrayOutputStream();
	            Writer writer = new OutputStreamWriter(baos , "UTF-8");
	            // TODO: include more params?
	            Hashtable<String, String[]> params = new Hashtable<String, String[]>();
	            String localId = null;
				try {
					localId = IdentifierManager.getInstance().getLocalId(pid.getValue());
				} catch (McdbDocNotFoundException e) {
					throw new NotFound("1020", e.getMessage());
				}
	            params.put("qformat", new String[] {format});	            
	            params.put("docid", new String[] {localId});
	            params.put("pid", new String[] {pid.getValue()});
	            transformer.transformXMLDocument(
	                    documentContent , 
	                    sourceType, 
	                    targetType , 
	                    format, 
	                    writer, 
	                    params, 
	                    null //sessionid
	                    );
	            
	            // finally, get the HTML back
	            resultInputStream = new ContentTypeByteArrayInputStream(baos.toByteArray());
	            ((ContentTypeByteArrayInputStream) resultInputStream).setContentType("text/html");
	
			} else {
				// just return the raw bytes
				resultInputStream = object;
			}
		} catch (IOException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		} catch (PropertyNotFoundException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		} catch (SQLException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		} catch (ClassNotFoundException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		}
		
		return resultInputStream;
	}	
    
}
