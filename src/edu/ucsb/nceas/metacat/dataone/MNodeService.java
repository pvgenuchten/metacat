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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.params.SolrParams;
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
import org.dataone.service.types.v1.AccessPolicy;
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
import org.dataone.service.types.v1.Group;
import org.dataone.service.types.v1.ObjectFormatIdentifier;
import org.dataone.service.types.v1.ObjectList;
import org.dataone.service.types.v1.Permission;
import org.dataone.service.types.v1.Ping;
import org.dataone.service.types.v1.Replica;
import org.dataone.service.types.v1.ReplicationStatus;
import org.dataone.service.types.v1.Schedule;
import org.dataone.service.types.v1.Service;
import org.dataone.service.types.v1.Services;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.Synchronization;
import org.dataone.service.types.v2.SystemMetadata;
import org.dataone.service.types.v2.TypeFactory;
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
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import edu.ucsb.nceas.ezid.EZIDException;
import edu.ucsb.nceas.metacat.DBQuery;
import edu.ucsb.nceas.metacat.DBTransform;
import edu.ucsb.nceas.metacat.EventLog;
import edu.ucsb.nceas.metacat.IdentifierManager;
import edu.ucsb.nceas.metacat.McdbDocNotFoundException;
import edu.ucsb.nceas.metacat.MetaCatServlet;
import edu.ucsb.nceas.metacat.MetacatHandler;
import edu.ucsb.nceas.metacat.ReadOnlyChecker;
import edu.ucsb.nceas.metacat.common.query.EnabledQueryEngines;
import edu.ucsb.nceas.metacat.common.query.stream.ContentTypeByteArrayInputStream;
import edu.ucsb.nceas.metacat.dataone.hazelcast.HazelcastService;
import edu.ucsb.nceas.metacat.dataone.resourcemap.ResourceMapModifier;
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
 * MNStorage.updateSystemMetadata()
 * MNReplication.replicate()
 * 
 */
public class MNodeService extends D1NodeService 
    implements MNAuthorization, MNCore, MNRead, MNReplication, MNStorage, MNQuery, MNView, MNPackage {

    //private static final String PATHQUERY = "pathquery";
	public static final String UUID_SCHEME = "UUID";
	public static final String DOI_SCHEME = "DOI";
	private static final String UUID_PREFIX = "urn:uuid:";
	
	private static String XPATH_EML_ID = "/eml:eml/@packageId";

	/* the logger instance */
    private Logger logMetacat = null;
    
    /* A reference to a remote Memeber Node */
    //private MNode mn;
    
    /* A reference to a Coordinating Node */
    private CNode cn;
    
    // shared executor
    private static ExecutorService executor = null;
    private boolean needSync = true;

    static {
        // use a shared executor service with nThreads == one less than available processors
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int nThreads = availableProcessors * 1;
        nThreads--;
        nThreads = Math.max(1, nThreads);
        executor = Executors.newFixedThreadPool(nThreads);  
    }


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

        try {
            needSync = (new Boolean(PropertyService.getProperty("dataone.nodeSynchronize"))).booleanValue();
        } catch (PropertyNotFoundException e) {
            // TODO Auto-generated catch block
            logMetacat.warn("MNodeService.constructor : can't find the property to indicate if the memeber node need to be synchronized. It will use the default value - true.");
        }
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
    public Identifier delete(Session session, Identifier id) 
        throws InvalidToken, ServiceFailure, NotAuthorized, NotFound, NotImplemented {

        String serviceFailureCode = "2902";
        String notFoundCode = "2901";
        String notAuthorizedCode = "2900";
        String invalidTokenCode = "2903";
        boolean needDeleteInfo = false;
        if(isReadOnlyMode()) {
            throw new ServiceFailure("2902", ReadOnlyChecker.DATAONEERROR);
        }
        
        Identifier HeadOfSid = getPIDForSID(id, serviceFailureCode);
        if(HeadOfSid != null) {
            id = HeadOfSid;
        }
        SystemMetadata sysmeta = null;
        try {
            sysmeta =getSystemMetadataForPID(id, serviceFailureCode, invalidTokenCode, notFoundCode, needDeleteInfo);
        } catch (InvalidRequest e) {
            throw new InvalidToken(invalidTokenCode, e.getMessage());
        }

        try {
            D1AuthHelper authDel = new D1AuthHelper(request, id, notAuthorizedCode, serviceFailureCode);
            //authDel.doAuthoritativeMNAuthorization(session, sysmeta);
            authDel.doAdminAuthorization(session);
        }
        catch (NotAuthorized na) {
            NotAuthorized na2 = new NotAuthorized(notAuthorizedCode, "The provided identity does not have permission to delete objects on the Node.");
            na2.initCause(na);
            throw na2;
        }
    	
    	   // defer to superclass implementation
        return super.delete(session.getSubject().getValue(), id);
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
        InvalidSystemMetadata, NotImplemented, InvalidRequest 
    {
        
        long startTime = System.currentTimeMillis();

        if(isReadOnlyMode()) {
            throw new ServiceFailure("1310", ReadOnlyChecker.DATAONEERROR);
        }

        //transform a sid to a pid if it is applicable
        String serviceFailureCode = "1310";
        Identifier sid = getPIDForSID(pid, serviceFailureCode);
        if(sid != null) {
            pid = sid;
        }

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

        // verify the new pid is valid format
        if (!isValidIdentifier(newPid)) {
            throw new InvalidRequest("1202", "The provided identifier is invalid.");
        }

        // make sure that the newPid doesn't exists
        boolean idExists = true;
        try {

            idExists = IdentifierManager.getInstance().identifierExists(newPid.getValue());
        } catch (SQLException e) {
            throw new ServiceFailure("1310", 
                    "The requested identifier " + newPid.getValue() +
                    " couldn't be determined if it is unique since : "+e.getMessage());
        }
        if (idExists) {
            throw new IdentifierNotUnique("1220", 
                    "The requested identifier " + newPid.getValue() +
                    " is already used by another object and" +
                    "therefore can not be used for this object. Clients should choose" +
                    "a new identifier that is unique and retry the operation or " +
                    "use CN.reserveIdentifier() to reserve one.");

        }



        // check for the existing identifier
        try {
            localId = IdentifierManager.getInstance().getLocalId(pid.getValue());

        } catch (McdbDocNotFoundException e) {
            throw new InvalidRequest("1202", "The object with the provided " + 
                    "identifier was not found.");

        } catch (SQLException ee) {
            throw new ServiceFailure("1310", "The object with the provided " + 
                    "identifier "+pid.getValue()+" can't be identified since - "+ee.getMessage());
        }

        long end =System.currentTimeMillis();
        logMetacat.debug("MNodeService.update - the time spending on checking the validation of the old pid "+pid.getValue()+" and the new pid "+newPid.getValue()+" is "+(end- startTime)+ " milli seconds.");

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
        long startTime2 = System.currentTimeMillis();
        // does the subject have WRITE ( == update) priveleges on the pid?
        //allowed = isAuthorized(session, pid, Permission.WRITE);
        //CN having the permission is allowed; user with the write permission and calling on the authoritative node is allowed.
        
        // get the existing system metadata for the object
        String invalidRequestCode = "1202";
        String notFoundCode ="1280";
        SystemMetadata existingSysMeta = getSystemMetadataForPID(pid, serviceFailureCode, invalidRequestCode, notFoundCode, true);
        try {
            D1AuthHelper authDel = new D1AuthHelper(request,pid,"1200","1310");
            authDel.doUpdateAuth(session, existingSysMeta, Permission.WRITE, this.getCurrentNodeId());
            allowed = true;
        } catch(ServiceFailure e) {
            throw new ServiceFailure("1310", "Can't determine if the client has the permission to update the object with id "+pid.getValue()+" since "+e.getDescription());
        } catch(NotAuthorized e) {
            throw new NotAuthorized("1200", "Can't update the object with id "+pid.getValue()+" since "+e.getDescription());
        }
        
        end =System.currentTimeMillis();
        logMetacat.debug("MNodeService.update - the time spending on checking if the user has the permission to update the old pid "+pid.getValue()+" with the new pid "+newPid.getValue()+" is "+(end- startTime2)+ " milli seconds.");

        if (allowed) {
            long startTime3 = System.currentTimeMillis();
            // check quality of SM
            if (sysmeta.getObsoletedBy() != null) {
                throw new InvalidSystemMetadata("1300", "Cannot include obsoletedBy when updating object");
            }
            if (sysmeta.getObsoletes() != null && !sysmeta.getObsoletes().getValue().equals(pid.getValue())) {
                throw new InvalidSystemMetadata("1300", "The identifier provided in obsoletes does not match old Identifier");
            }

            
            //System.out.println("the archive is "+existingSysMeta.getArchived());
            //Base on documentation, we can't update an archived object:
            //The update operation MUST fail with Exceptions.InvalidRequest on objects that have the Types.SystemMetadata.archived property set to true.
            if(existingSysMeta.getArchived() != null && existingSysMeta.getArchived()) {
                throw new InvalidRequest("1202","An archived object"+pid.getValue()+" can't be updated");
            }

            // check for previous update
            // see: https://redmine.dataone.org/issues/3336
            Identifier existingObsoletedBy = existingSysMeta.getObsoletedBy();
            if (existingObsoletedBy != null) {
                throw new InvalidRequest("1202", 
                        "The previous identifier has already been made obsolete by: " + existingObsoletedBy.getValue());
            }
            end =System.currentTimeMillis();
            logMetacat.debug("MNodeService.update - the time spending on checking the quality of the system metadata of the old pid "+pid.getValue()+" and the new pid "+newPid.getValue()+" is "+(end- startTime3)+ " milli seconds.");

            //check the sid in the system metadata. If it exists, it should be non-exist or match the old sid in the previous system metadata.
            Identifier sidInSys = sysmeta.getSeriesId();
            if(sidInSys != null) {
                if (!isValidIdentifier(sidInSys)) {
                    throw new InvalidSystemMetadata("1300", "The provided series id in the system metadata is invalid.");
                }
                Identifier previousSid = existingSysMeta.getSeriesId();
                if(previousSid != null) {
                    // there is a previous sid, if the new sid doesn't match it, the new sid should be non-existing.
                    if(!sidInSys.getValue().equals(previousSid.getValue())) {
                        try {
                            idExists = IdentifierManager.getInstance().identifierExists(sidInSys.getValue());
                        } catch (SQLException e) {
                            throw new ServiceFailure("1310", 
                                    "The requested identifier " + sidInSys.getValue() +
                                    " couldn't be determined if it is unique since : "+e.getMessage());
                        }
                        if(idExists) {
                            throw new InvalidSystemMetadata("1300", "The series id "+sidInSys.getValue()+" in the system metadata doesn't match the previous series id "
                                    +previousSid.getValue()+", so it should NOT exist. However, it was used by another object.");
                        }
                    }
                } else {
                    // there is no previous sid, the new sid should be non-existing.
                    try {
                        idExists = IdentifierManager.getInstance().identifierExists(sidInSys.getValue());
                    } catch (SQLException e) {
                        throw new ServiceFailure("1310", 
                                "The requested identifier " + sidInSys.getValue() +
                                " couldn't be determined if it is unique since : "+e.getMessage());
                    }
                    if(idExists) {
                        throw new InvalidSystemMetadata("1300", "The series id "+sidInSys.getValue()+" in the system metadata should NOT exist since the previous series id is null."
                                +"However, it was used by another object.");
                    }
                }
                //the series id equals the pid (new pid hasn't been registered in the system, so IdentifierManager.getInstance().identifierExists method can't exclude this scenario)
                if(sidInSys.getValue().equals(newPid.getValue())) {
                    throw new InvalidSystemMetadata("1300", "The series id "+sidInSys.getValue()+" in the system metadata shouldn't have the same value of the pid.");
                }
            }

            long end2 =System.currentTimeMillis();
            logMetacat.debug("MNodeService.update - the time spending on checking the sid validation of the old pid "+pid.getValue()+" and the new pid "+newPid.getValue()+" is "+(end2- end)+ " milli seconds.");


            isScienceMetadata = isScienceMetadata(sysmeta);

            // do we have XML metadata or a data object?
            if (isScienceMetadata) {

                // update the science metadata XML document
                // TODO: handle non-XML metadata/data documents (like netCDF)
                // TODO: don't put objects into memory using stream to string
                //String objectAsXML = "";
                try {
                    //objectAsXML = IOUtils.toString(object, "UTF-8");
                    String formatId = null;
                    if(sysmeta.getFormatId() != null) {
                        formatId = sysmeta.getFormatId().getValue();
                    }
                    localId = insertOrUpdateDocument(object, "UTF-8", pid, session, "update", formatId, sysmeta.getChecksum());

                    // register the newPid and the generated localId
                    if (newPid != null) {
                        IdentifierManager.getInstance().createMapping(newPid.getValue(), localId);
                    }

                } catch (IOException e) {
                    String msg = "The Node is unable to create the object: "+pid.getValue() + "There was a problem converting the object to XML";
                    logMetacat.error(msg, e);
                    removeIdFromIdentifierTable(newPid);
                    throw new ServiceFailure("1310", msg + ": " + e.getMessage());

                }  catch (PropertyNotFoundException e) {
                    String msg = "The Node is unable to create the object. " +pid.getValue()+ " since the properties are not configured well "+e.getMessage();
                    logMetacat.error(msg, e);
                    removeIdFromIdentifierTable(newPid);
                    throw new ServiceFailure("1310", msg);
                } catch (Exception e) {
                    logMetacat.error("MNService.update - couldn't write the metadata object to the disk since "+e.getMessage(), e);
                    removeIdFromIdentifierTable(newPid);
                    throw e;
                }

            } else {

                // update the data object
                try {
                    localId = insertDataObject(object, newPid, session, sysmeta.getChecksum());
                } catch (Exception e) {
                    logMetacat.error("MNService.update - couldn't write the data object to the disk since "+e.getMessage(), e);
                    removeIdFromIdentifierTable(newPid);
                    throw e;
                }


            }

            long end3 =System.currentTimeMillis();
            logMetacat.debug("MNodeService.update - the time spending on saving the object with the new pid "+newPid.getValue()+" is "+(end3- end2)+ " milli seconds.");

            // add the newPid to the obsoletedBy list for the existing sysmeta
            existingSysMeta.setObsoletedBy(newPid);
            //increase version
            BigInteger current = existingSysMeta.getSerialVersion();
            //System.out.println("the current version is "+current);
            current = current.add(BigInteger.ONE);
            //System.out.println("the new current version is "+current);
            existingSysMeta.setSerialVersion(current);
            // then update the existing system metadata
            updateSystemMetadata(existingSysMeta);

            // prep the new system metadata, add pid to the affected lists
            sysmeta.setObsoletes(pid);
            //sysmeta.addDerivedFrom(pid);

            // and insert the new system metadata
            insertSystemMetadata(sysmeta);

            // log the update event
            EventLog.getInstance().log(request.getRemoteAddr(), request.getHeader("User-Agent"), subject.getValue(), localId, Event.UPDATE.toString());

            long end4 =System.currentTimeMillis();
            logMetacat.debug("MNodeService.update - the time spending on updating/saving system metadata  of the old pid "+pid.getValue()+" and the new pid "+newPid.getValue()+" and saving the log information is "+(end4- end3)+ " milli seconds.");

            // attempt to register the identifier - it checks if it is a doi
            try {
                DOIService.getInstance().registerDOI(sysmeta);
            } catch (Exception e) {
                throw new ServiceFailure("1190", "Could not register DOI: " + e.getMessage());
            }
            long end5 =System.currentTimeMillis();
            logMetacat.debug("MNodeService.update - the time spending on registering the doi (if it is doi ) of the new pid "+newPid.getValue()+" is "+(end5- end4)+ " milli seconds.");

        } else {
            throw new NotAuthorized("1200", "The provided identity does not have " + "permission to UPDATE the object identified by " + pid.getValue()
            + " on the Member Node.");
        }

        long end6 =System.currentTimeMillis();
        logMetacat.debug("MNodeService.update - the total time of updating the old pid " +pid.getValue() +" whth the new pid "+newPid.getValue()+" is "+(end6- startTime)+ " milli seconds.");
        return newPid;
    }
    
    /*
     * Roll-back method when inserting data object fails.
     */
    protected void removeIdFromIdentifierTable(Identifier id){
        if(id != null) {
            try {
                if(IdentifierManager.getInstance().mappingExists(id.getValue())) {
                   String localId = IdentifierManager.getInstance().getLocalId(id.getValue());
                   IdentifierManager.getInstance().removeMapping(id.getValue(), localId);
                   logMetacat.info("MNodeService.removeIdFromIdentifierTable - the identifier "+id.getValue()+" and local id "+localId+" have been removed from the identifier table since the object creation failed");
                }
            } catch (Exception e) {
                logMetacat.warn("MNodeService.removeIdFromIdentifierTable - can't decide if the mapping of  the pid "+id.getValue()+" exists on the identifier table.");
            }
        }
    }

    public Identifier create(Session session, Identifier pid, InputStream object, SystemMetadata sysmeta) throws InvalidToken, ServiceFailure, NotAuthorized,
            IdentifierNotUnique, UnsupportedType, InsufficientResources, InvalidSystemMetadata, NotImplemented, InvalidRequest {

        if(isReadOnlyMode()) {
            throw new ServiceFailure("1190", ReadOnlyChecker.DATAONEERROR);
        }
        // check for null session
        if (session == null) {
            throw new InvalidToken("1110", "Session is required to WRITE to the Node.");
        }

        // verify the pid is valid format
        if (!isValidIdentifier(pid)) {
            throw new InvalidRequest("1102", "The provided identifier is invalid.");
        }
        objectExists(pid);
        // set the submitter to match the certificate
        sysmeta.setSubmitter(session.getSubject());
        // set the originating node
        NodeReference originMemberNode = this.getCapabilities().getIdentifier();
        sysmeta.setOriginMemberNode(originMemberNode);
        
        // if no authoritative MN, set it to the same
        if (sysmeta.getAuthoritativeMemberNode() == null || sysmeta.getAuthoritativeMemberNode().getValue().trim().equals("") ||
                sysmeta.getAuthoritativeMemberNode().getValue().equals("null")) {
            sysmeta.setAuthoritativeMemberNode(originMemberNode);
        }

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
        
        // verify the sid in the system metadata
        Identifier sid = sysmeta.getSeriesId();
        boolean idExists = false;
        if(sid != null) {
            if (!isValidIdentifier(sid)) {
                throw new InvalidSystemMetadata("1180", "The provided series id is invalid.");
            }
            try {
                idExists = IdentifierManager.getInstance().identifierExists(sid.getValue());
            } catch (SQLException e) {
                throw new ServiceFailure("1190", 
                                        "The series identifier " + sid.getValue() +
                                        " in the system metadata couldn't be determined if it is unique since : "+e.getMessage());
            }
            if (idExists) {
                    throw new InvalidSystemMetadata("1180", 
                              "The series identifier " + sid.getValue() +
                              " is already used by another object and " +
                              "therefore can not be used for this object. Clients should choose" +
                              " a new identifier that is unique and retry the operation or " +
                              "use CN.reserveIdentifier() to reserve one.");
                
            }
            //the series id equals the pid (new pid hasn't been registered in the system, so IdentifierManager.getInstance().identifierExists method can't exclude this scenario )
            if(sid.getValue().equals(pid.getValue())) {
                throw new InvalidSystemMetadata("1180", "The series id "+sid.getValue()+" in the system metadata shouldn't have the same value of the pid.");
            }
        }
        
        boolean allowed = false;
        try {
          allowed = isAuthorized(session, pid, Permission.WRITE);
                
        } catch (NotFound e) {
          // The identifier doesn't exist, writing should be fine.
          allowed = true;
        }
        
        if(!allowed) {
            throw new NotAuthorized("1100", "Provited Identity doesn't have the WRITE permission on the pid "+pid.getValue());
        }
        logMetacat.debug("Allowed to create: " + pid.getValue());

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
        /*if(isReadOnlyMode()) {
            throw new InvalidRequest("2153", "The Metacat member node is on the read-only mode and your request can't be fulfiled. Please try again later.");
        }*/

        if (session != null && sysmeta != null && sourceNode != null) {
            logMetacat.info("MNodeService.replicate() called with parameters: \n" +
                            "\tSession.Subject      = "                           +
                            session.getSubject().getValue() + "\n"                +
                            "\tidentifier           = "                           + 
                            sysmeta.getIdentifier().getValue()                    +
                            "\n" + "\tSource NodeReference ="                     +
                            sourceNode.getValue());
        } else {
            throw new InvalidRequest("2153", "The provided session or systemmetdata or sourceNode should NOT be null.");
        }
        boolean result = false;
        String nodeIdStr = null;
        NodeReference nodeId = null;

        // get the referenced object
        Identifier pid = sysmeta.getIdentifier();
        // verify the pid is valid format
        if (!isValidIdentifier(pid)) {
            throw new InvalidRequest("2153", "The provided identifier in the system metadata is invalid.");
        }

        // get from the membernode
        // TODO: switch credentials for the server retrieval?
        
 //       this.cn = D1Client.getCN();
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
       
        // only allow cns call this method
        D1AuthHelper authDel = new D1AuthHelper(request, sysmeta.getIdentifier(), "2152","2151");
        authDel.doCNOnlyAuthorization(session);
 

        logMetacat.debug("Allowed to replicate: " + pid.getValue());

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
                    
                } catch (SQLException ee) {
                    throw new ServiceFailure("2151", "Couldn't identify the local id of the object with the specified identifier "
                                            +pid.getValue()+" since - "+ee.getMessage());
                }
                
                // no local replica, get a replica
                if ( object == null ) {
                    /*boolean success = true;
                    try {
                        //use the v2 ping api to connect the source node
                        mn.ping();
                    } catch (Exception e) {
                        success = false;
                    }*/
                    D1NodeVersionChecker checker = new D1NodeVersionChecker(sourceNode);
                    String nodeVersion = checker.getVersion("MNRead");
                    if(nodeVersion != null && nodeVersion.equals(D1NodeVersionChecker.V1)) {
                        //The source node is a v1 node, we use the v1 api
                        org.dataone.client.v1.MNode mNodeV1 =  org.dataone.client.v1.itk.D1Client.getMN(sourceNode);
                        object = mNodeV1.getReplica(thisNodeSession, pid);
                    } else if (nodeVersion != null && nodeVersion.equals(D1NodeVersionChecker.V2)){
                     // session should be null to use the default certificate
                        // location set in the Certificate manager
                        MNode mn = D1Client.getMN(sourceNode);
                        object = mn.getReplica(thisNodeSession, pid);
                    } else {
                        throw new ServiceFailure("2151", "The version of MNRead service is "+nodeVersion+" in the source node "+sourceNode.getValue()+" and it is not supported. Please check the information in the cn");
                    }
                    
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
            logMetacat.info("MNodeService.replicate - the class of object inputstream is "+object.getClass().getCanonicalName()+". Does it support the reset method? The answer is "+object.markSupported());

            // add it to local store
            Identifier retPid;
            try {
                // skip the MN.create -- this mutates the system metadata and we don't want it to
                if ( localId == null ) {
                    // TODO: this will fail if we already "know" about the identifier
                    // FIXME: see https://redmine.dataone.org/issues/2572
                    objectExists(pid);
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
        } finally {
            IOUtils.closeQuietly(object);
        }
        

        // finish by setting the replication status
        setReplicationStatus(thisNodeSession, pid, nodeId, ReplicationStatus.COMPLETED, null);
        return result;

    }
    
    /*
     * If the given node supports v2 replication.
     */
    private boolean supportV2Replication(Node node) throws InvalidRequest {
        return supportVersionReplication(node, "v2");
    }
    
    /*
     * If the given node support the the given version replication. Return true if it does.
     */
    private boolean supportVersionReplication(Node node, String version) throws InvalidRequest{
        boolean support = false;
        if(node == null) {
            throw new InvalidRequest("2153", "There is no capacity information about the node in the replicate.");
        } else {
            Services services = node.getServices();
            if(services == null) {
                throw new InvalidRequest("2153", "Can't get replica from a node which doesn't have the replicate service.");
            } else {
               List<Service> list = services.getServiceList();
               if(list == null) {
                   throw new InvalidRequest("2153", "Can't get replica from a node which doesn't have the replicate service.");
               } else {
                   for(Service service : list) {
                       if(service != null && service.getName() != null && service.getName().equals("MNReplication") && 
                               service.getVersion() != null && service.getVersion().equalsIgnoreCase(version) && service.getAvailable() == true ) {
                           support = true;
                           
                       }
                   }
               }
            }
        }
        return support;
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
        String serviceFailure = "1410";
        String notFound = "1420";
        //Checkum only handles the pid, not sid
        checkV1SystemMetaPidExist(pid, serviceFailure, "The checksum for the object specified by "+pid.getValue()+" couldn't be returned ",  notFound, 
                "The object specified by "+pid.getValue()+" does not exist at this node.");
        InputStream inputStream = get(session, pid);

        try {
            checksum = ChecksumUtil.checksum(inputStream, algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceFailure("1410", "The checksum for the object specified by " + pid.getValue() + "could not be returned due to an internal error: "
                    + e.getMessage());
        } catch (IOException e) {
            throw new ServiceFailure("1410", "The checksum for the object specified by " + pid.getValue() + "could not be returned due to an internal error: "
                    + e.getMessage());
        } finally {
            if(inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception e) {
                   logMetacat.warn("MNodeService.getChecksum - can't close the input stream which got the object content since "+e.getMessage());
                }
            }
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
        NodeReference nodeId = null;
        if(!replicaStatus) {
            //not include those objects whose authoritative node is not this mn
            nodeId = new NodeReference();
            try {
                String currentNodeId = PropertyService.getInstance().getProperty("dataone.nodeId"); // return only pids for which this mn
                nodeId.setValue(currentNodeId);
            } catch(Exception e) {
                throw new ServiceFailure("1580", e.getMessage());
            }
        }
        return super.listObjects(session, startTime, endTime, objectFormatId, identifier, nodeId, start, count);
    }

    /**
     * Return a description of the node's capabilities and services.
     * 
     * @return node - the technical capabilities of the Member Node
     * 
     * @throws ServiceFailure
     * @throws NotAuthorized
     * @throws InvalidRequest
     * @throws NotImplemented - not thrown by this implementation
     */
    @Override
    public Node getCapabilities() 
        throws ServiceFailure {

        String nodeName = null;
        String nodeId = null;
        String subject = null;
        String contactSubject = null;
        String nodeDesc = null;
        String nodeTypeString = null;
        NodeType nodeType = null;
        List<String> mnCoreServiceVersions = null;
        List<String> mnReadServiceVersions = null;
        List<String> mnAuthorizationServiceVersions = null;
        List<String> mnStorageServiceVersions = null;
        List<String> mnReplicationServiceVersions = null;
        List<String> mnPackageServiceVersions = null;
        List<String> mnQueryServiceVersions = null;
        List<String> mnViewServiceVersions = null;

        boolean nodeSynchronize = false;
        boolean nodeReplicate = false;
        List<String> mnCoreServiceAvailables = null;
        List<String> mnReadServiceAvailables = null;
        List<String> mnAuthorizationServiceAvailables = null;
        List<String> mnStorageServiceAvailables = null;
        List<String> mnReplicationServiceAvailables = null;
        List<String> mnPackageServiceAvailables = null;
        List<String> mnQueryServiceAvailables = null;
        List<String> mnViewServiceAvailables = null;

        try {
            // get the properties of the node based on configuration information
            nodeName = Settings.getConfiguration().getString("dataone.nodeName");
            nodeId = Settings.getConfiguration().getString("dataone.nodeId");
            subject = Settings.getConfiguration().getString("dataone.subject");
            contactSubject = Settings.getConfiguration().getString("dataone.contactSubject");
            nodeDesc = Settings.getConfiguration().getString("dataone.nodeDescription");
            nodeTypeString = Settings.getConfiguration().getString("dataone.nodeType");
            nodeType = NodeType.convert(nodeTypeString);
            nodeSynchronize = new Boolean(Settings.getConfiguration().getString("dataone.nodeSynchronize")).booleanValue();
            nodeReplicate = new Boolean(Settings.getConfiguration().getString("dataone.nodeReplicate")).booleanValue();

            // Set the properties of the node based on configuration information and
            // calls to current status methods
            String serviceName = SystemUtil.getContextURL() + "/" + PropertyService.getProperty("dataone.serviceName");
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

            mnCoreServiceVersions = Settings.getConfiguration().getList("dataone.mnCore.serviceVersion");
            mnCoreServiceAvailables = Settings.getConfiguration().getList("dataone.mnCore.serviceAvailable");
            if(mnCoreServiceVersions != null && mnCoreServiceAvailables != null && mnCoreServiceVersions.size() == mnCoreServiceAvailables.size()) {
                for(int i=0; i<mnCoreServiceVersions.size(); i++) {
                    String version = mnCoreServiceVersions.get(i);
                    boolean available = new Boolean(mnCoreServiceAvailables.get(i)).booleanValue();
                    Service sMNCore = new Service();
                    sMNCore.setName("MNCore");
                    sMNCore.setVersion(version);
                    sMNCore.setAvailable(available);
                    services.addService(sMNCore);
                }
            }
            
            mnReadServiceVersions = Settings.getConfiguration().getList("dataone.mnRead.serviceVersion");
            mnReadServiceAvailables = Settings.getConfiguration().getList("dataone.mnRead.serviceAvailable");
            if(mnReadServiceVersions != null && mnReadServiceAvailables != null && mnReadServiceVersions.size()==mnReadServiceAvailables.size()) {
                for(int i=0; i<mnReadServiceVersions.size(); i++) {
                    String version = mnReadServiceVersions.get(i);
                    boolean available = new Boolean(mnReadServiceAvailables.get(i)).booleanValue();
                    Service sMNRead = new Service();
                    sMNRead.setName("MNRead");
                    sMNRead.setVersion(version);
                    sMNRead.setAvailable(available);
                    services.addService(sMNRead);
                }
            }
           
            mnAuthorizationServiceVersions = Settings.getConfiguration().getList("dataone.mnAuthorization.serviceVersion");
            mnAuthorizationServiceAvailables = Settings.getConfiguration().getList("dataone.mnAuthorization.serviceAvailable");
            if(mnAuthorizationServiceVersions != null && mnAuthorizationServiceAvailables != null && mnAuthorizationServiceVersions.size()==mnAuthorizationServiceAvailables.size()) {
                for(int i=0; i<mnAuthorizationServiceVersions.size(); i++) {
                    String version = mnAuthorizationServiceVersions.get(i);
                    boolean available = new Boolean(mnAuthorizationServiceAvailables.get(i)).booleanValue();
                    Service sMNAuthorization = new Service();
                    sMNAuthorization.setName("MNAuthorization");
                    sMNAuthorization.setVersion(version);
                    sMNAuthorization.setAvailable(available);
                    services.addService(sMNAuthorization);
                }
            }
           
            mnStorageServiceVersions = Settings.getConfiguration().getList("dataone.mnStorage.serviceVersion");
            mnStorageServiceAvailables = Settings.getConfiguration().getList("dataone.mnStorage.serviceAvailable");
            if(mnStorageServiceVersions != null && mnStorageServiceAvailables != null && mnStorageServiceVersions.size() == mnStorageServiceAvailables.size()) {
                for(int i=0; i<mnStorageServiceVersions.size(); i++) {
                    String version = mnStorageServiceVersions.get(i);
                    boolean available = new Boolean(mnStorageServiceAvailables.get(i)).booleanValue();
                    Service sMNStorage = new Service();
                    sMNStorage.setName("MNStorage");
                    sMNStorage.setVersion(version);
                    sMNStorage.setAvailable(available);
                    services.addService(sMNStorage);
                }
            }
            
            mnReplicationServiceVersions = Settings.getConfiguration().getList("dataone.mnReplication.serviceVersion");
            mnReplicationServiceAvailables = Settings.getConfiguration().getList("dataone.mnReplication.serviceAvailable");
            if(mnReplicationServiceVersions != null && mnReplicationServiceAvailables != null && mnReplicationServiceVersions.size() == mnReplicationServiceAvailables.size()) {
                for (int i=0; i<mnReplicationServiceVersions.size(); i++) {
                    String version = mnReplicationServiceVersions.get(i);
                    boolean available = new Boolean(mnReplicationServiceAvailables.get(i)).booleanValue();
                    Service sMNReplication = new Service();
                    sMNReplication.setName("MNReplication");
                    sMNReplication.setVersion(version);
                    sMNReplication.setAvailable(available);
                    services.addService(sMNReplication);
                }
            }
            
            mnPackageServiceVersions = Settings.getConfiguration().getList("dataone.mnPackage.serviceVersion");
            mnPackageServiceAvailables = Settings.getConfiguration().getList("dataone.mnPackage.serviceAvailable");
            if(mnPackageServiceVersions != null && mnPackageServiceAvailables != null && mnPackageServiceVersions.size() == mnPackageServiceAvailables.size()) {
                for (int i=0; i<mnPackageServiceVersions.size(); i++) {
                    String version = mnPackageServiceVersions.get(i);
                    boolean available = new Boolean(mnPackageServiceAvailables.get(i)).booleanValue();
                    Service sMNPakcage = new Service();
                    sMNPakcage.setName("MNPackage");
                    sMNPakcage.setVersion(version);
                    sMNPakcage.setAvailable(available);
                    services.addService(sMNPakcage);
                }
            }
            
            mnQueryServiceVersions = Settings.getConfiguration().getList("dataone.mnQuery.serviceVersion");
            mnQueryServiceAvailables = Settings.getConfiguration().getList("dataone.mnQuery.serviceAvailable");
            if(mnQueryServiceVersions != null && mnQueryServiceAvailables != null && mnQueryServiceVersions.size() == mnQueryServiceAvailables.size()) {
                for (int i=0; i<mnQueryServiceVersions.size(); i++) {
                    String version = mnQueryServiceVersions.get(i);
                    boolean available = new Boolean(mnQueryServiceAvailables.get(i)).booleanValue();
                    Service sMNQuery = new Service();
                    sMNQuery.setName("MNQuery");
                    sMNQuery.setVersion(version);
                    sMNQuery.setAvailable(available);
                    services.addService(sMNQuery);
                }
            }
            
            mnViewServiceVersions = Settings.getConfiguration().getList("dataone.mnView.serviceVersion");
            mnViewServiceAvailables = Settings.getConfiguration().getList("dataone.mnView.serviceAvailable");
            if(mnViewServiceVersions != null && mnViewServiceAvailables != null && mnViewServiceVersions.size() == mnViewServiceAvailables.size()) {
                for (int i=0; i<mnViewServiceVersions.size(); i++) {
                    String version = mnViewServiceVersions.get(i);
                    boolean available = new Boolean(mnViewServiceAvailables.get(i)).booleanValue();
                    Service sMNView = new Service();
                    sMNView.setName("MNView");
                    sMNView.setVersion(version);
                    sMNView.setAvailable(available);
                    services.addService(sMNView);
                }
            }
            
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
        
        if ( syncFailed.getPid() == null ) {
            throw new ServiceFailure("2161", "The identifier cannot be null.");
        }
        Identifier pid = new Identifier();
        pid.setValue(syncFailed.getPid());
        boolean allowed;
            
        //are we allowed? only CNs
        D1AuthHelper authDel = new D1AuthHelper(request, pid, "2162","2161");
        authDel.doCNOnlyAuthorization(session);

        
        try {
            localId = IdentifierManager.getInstance().getLocalId(pid.getValue());
        } catch (McdbDocNotFoundException e) {
            throw new ServiceFailure("2161", "The identifier specified by " + 
                    syncFailed.getPid() + " was not found on this node.");

        } catch (SQLException e) {
            throw new ServiceFailure("2161", "Couldn't identify the local id of the identifier specified by " + 
                    syncFailed.getPid() + " since "+e.getMessage());
        }
        // TODO: update the CN URL below when the CNRead.SynchronizationFailed
        // method is changed to include the URL as a parameter
        logMetacat.warn("Synchronization for the object identified by " + 
                pid.getValue() + " failed from " + syncFailed.getNodeId() + 
                " with message: " + syncFailed.getDescription() + 
                ". Logging the event to the Metacat EventLog as a 'syncFailed' event.");
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
        throws NotAuthorized, NotImplemented, ServiceFailure, InvalidToken, NotFound {

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
            throw new NotFound("2185", "The object specified by " + 
                    pid.getValue() + " does not exist at this node.");
            
        } catch (SQLException e) {
            throw new ServiceFailure("2181", "The local id of the object specified by " + 
                    pid.getValue() + " couldn't be identified since "+e.getMessage());
        }

        Subject targetNodeSubject = session.getSubject();

        // check for authorization to replicate, null session to act as this source MN
        try {
            allowed = D1Client.getCN().isNodeAuthorized(null, targetNodeSubject, pid);
        } catch (InvalidToken e1) {
            throw new ServiceFailure("2181", "Could not determine if node is authorized: " 
                + e1.getMessage());
            
        } catch (NotFound e1) {
            throw new NotFound("2185", "Could not find the object "+pid.getValue()+" in this node - " 
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
                throw new ServiceFailure("2181", "The object specified by " + 
                    pid.getValue() + "could not be returned due to error: " + e.getMessage());
            }
        } else {
            throw new NotAuthorized("2182", "The pid "+pid.getValue()+" is not authorized to be read by the client.");
        }

        // if we fail to set the input stream
        if (inputStream == null) {
            throw new ServiceFailure("2181", "The object specified by " + 
                pid.getValue() + " can't be returned from the node.");
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
        boolean needCheckAuthoriativeNode = true; 
        return systemMetadataChanged(needCheckAuthoriativeNode, session, pid,serialVersion, dateSysMetaLastModified);
    }

    /**
     * A method to notify the Member Node that the authoritative copy of 
     * system metadata on the Coordinating Nodes has changed.
     * @param needCheckAuthoriativeNode  this is for the dataone version 2. In the
     * version 2, there are two scenarios:
     * 1. If the node is the authoritative node, it only accepts serial version and replica list.
     * 2. If the node is a replica, it accepts everything.
     * For the v1, api, the parameter should be false. 
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
    public boolean systemMetadataChanged(boolean needCheckAuthoriativeNode, Session session, Identifier pid,
        long serialVersion, Date dateSysMetaLastModified) 
        throws NotImplemented, ServiceFailure, NotAuthorized, InvalidRequest,
        InvalidToken {
        
        if(isReadOnlyMode()) {
            throw new InvalidRequest("1334", "The Metacat member node is on the read-only mode and your request can't be fulfiled. Please try again later.");
        }
        // cannot be called by public
        if (session == null) {
            throw new InvalidToken("1332", "No session was provided.");
        }

        String serviceFailureCode = "1333";
        Identifier sid = getPIDForSID(pid, serviceFailureCode);
        if(sid != null) {
            pid = sid;
        }
        
        SystemMetadata currentLocalSysMeta = null;
        SystemMetadata newSysMeta = null;
        
        D1AuthHelper authDel = new D1AuthHelper(request, pid, "1331", serviceFailureCode);
        authDel.doCNOnlyAuthorization(session);
        try {
            HazelcastService.getInstance().getSystemMetadataMap().lock(pid);
        
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
            
            if(currentLocalSysMeta == null) {
                throw new InvalidRequest("1334", "We can't find the system metadata in the node for the id "+pid.getValue());
            }
            if (currentLocalSysMeta.getSerialVersion().longValue() <= serialVersion ) {
                try {
                    this.cn = D1Client.getCN();
                    newSysMeta = cn.getSystemMetadata(null, pid);
                } catch (NotFound e) {
                    // huh? you just said you had it
                	String msg = "On updating the local copy of system metadata " + 
                    "for pid " + pid.getValue() +", the CN reports it is not found." +
                    " The error message was: " + e.getMessage();
                    logMetacat.error(msg);
                    //ServiceFailure sf = new ServiceFailure("1333", msg);
                    InvalidRequest sf = new InvalidRequest("1334", msg);
                    sf.initCause(e);
                    throw sf;
                }
                
                //check about the sid in the system metadata
                Identifier newSID = newSysMeta.getSeriesId();
                if(newSID != null) {
                    if (!isValidIdentifier(newSID)) {
                        throw new InvalidRequest("1334", "The series identifier in the new system metadata is invalid.");
                    }
                    Identifier currentSID = currentLocalSysMeta.getSeriesId();
                    if( currentSID != null && currentSID.getValue() != null) {
                        if(!newSID.getValue().equals(currentSID.getValue())) {
                            //newSID doesn't match the currentSID. The newSID shouldn't be used.
                            try {
                                if(IdentifierManager.getInstance().identifierExists(newSID.getValue())) {
                                    throw new InvalidRequest("1334", "The series identifier "+newSID.getValue()+" in the new system metadata has been used by another object.");
                                }
                            } catch (SQLException sql) {
                                throw new ServiceFailure("1333", "Couldn't determine if the SID "+newSID.getValue()+" in the system metadata exists in the node since "+sql.getMessage());
                            }
                            
                        }
                    } else {
                        //newSID shouldn't be used
                        try {
                            if(IdentifierManager.getInstance().identifierExists(newSID.getValue())) {
                                throw new InvalidRequest("1334", "The series identifier "+newSID.getValue()+" in the new system metadata has been used by another object.");
                            }
                        } catch (SQLException sql) {
                            throw new ServiceFailure("1333", "Couldn't determine if the SID "+newSID.getValue()+" in the system metadata exists in the node since "+sql.getMessage());
                        }
                    }
                }
                // update the local copy of system metadata for the pid
                try {
                    if(needCheckAuthoriativeNode) {
                        //this is for the v2 api.
                        if(isAuthoritativeNode(pid)) {
                            //this is the authoritative node, so we only accept replica and serial version
                            logMetacat.debug("MNodeService.systemMetadataChanged - this is the authoritative node for the pid "+pid.getValue());
                            List<Replica> replicas = newSysMeta.getReplicaList();
                            newSysMeta = currentLocalSysMeta;
                            newSysMeta.setSerialVersion(new BigInteger((new Long(serialVersion)).toString()));
                            newSysMeta.setReplicaList(replicas);
                        } else {
                            //we need to archive the object in the replica node
                            logMetacat.debug("MNodeService.systemMetadataChanged - this is NOT the authoritative node for the pid "+pid.getValue());
                            logMetacat.debug("MNodeService.systemMetadataChanged - the new value of archive is "+newSysMeta.getArchived()+" for the pid "+pid.getValue());
                            logMetacat.debug("MNodeService.systemMetadataChanged - the local value of archive is "+currentLocalSysMeta.getArchived()+" for the pid "+pid.getValue());
                            if (newSysMeta.getArchived() != null && newSysMeta.getArchived() == true  && 
                                    ((currentLocalSysMeta.getArchived() != null && currentLocalSysMeta.getArchived() == false ) || currentLocalSysMeta.getArchived() == null)){
                                logMetacat.debug("MNodeService.systemMetadataChanged - start to archive object "+pid.getValue());
                                boolean logArchive = false;
                                boolean needUpdateModificationDate = false;
                                try {
                                    archiveObject(logArchive, session, pid, newSysMeta, needUpdateModificationDate);
                                } catch (NotFound e) {
                                    throw new InvalidRequest("1334", "Can't find the pid "+pid.getValue()+" for archive.");
                                }
                                
                            } else if((newSysMeta.getArchived() == null || newSysMeta.getArchived() == false) && (currentLocalSysMeta.getArchived() != null && currentLocalSysMeta.getArchived() == true )) {
                                throw new InvalidRequest("1334", "The pid "+pid.getValue()+" has been archived and it can't be reset to false.");
                            }
                        }
                    }
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
                
                try {
                    String localId = IdentifierManager.getInstance().getLocalId(pid.getValue());
                    EventLog.getInstance().log(request.getRemoteAddr(), 
                            request.getHeader("User-Agent"), session.getSubject().getValue(), 
                            localId, "updateSystemMetadata");
                } catch (Exception e) {
                    // do nothing, no localId to log with
                    logMetacat.warn("MNodeService.systemMetadataChanged - Could not log 'updateSystemMetadata' event because no localId was found for pid: " + pid.getValue());
                } 
                
               
            }
        } finally {
            HazelcastService.getInstance().getSystemMetadataMap().unlock(pid);
        }
        
        if (currentLocalSysMeta.getSerialVersion().longValue() <= serialVersion ) {
            // attempt to re-register the identifier (it checks if it is a doi)
            try {
                DOIService.getInstance().registerDOI(newSysMeta);
            } catch (Exception e) {
                logMetacat.warn("Could not [re]register DOI: " + e.getMessage(), e);
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
		    if(sysmeta.getAccessPolicy() != null) {
		        sysmeta.getAccessPolicy().addAllow(publicRule);
		    } else {
		        AccessPolicy policy = new AccessPolicy();
		        policy.addAllow(publicRule);
		        sysmeta.setAccessPolicy(policy);
		    }
			
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
        Set<Subject> subjects = getQuerySubjects(session);
        boolean isMNadmin = isMNAdminQuery(session);
		if (engine != null && engine.equals(EnabledQueryEngines.PATHQUERYENGINE)) {
		    if(!EnabledQueryEngines.getInstance().isEnabled(EnabledQueryEngines.PATHQUERYENGINE)) {
                throw new NotImplemented("0000", "MNodeService.query - the query engine "+engine +" hasn't been implemented or has been disabled.");
            }
		    String user = Constants.SUBJECT_PUBLIC;
	        String[] groups= null;
	        if (session != null) {
	            user = session.getSubject().getValue();
	        }
            if (subjects != null) {
                List<String> groupList = new ArrayList<String>();
                for (Subject subject: subjects) {
                    groupList.add(subject.getValue());
                }
                groups = groupList.toArray(new String[0]);
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
		        
                return MetacatSolrIndex.getInstance().query(query, subjects, isMNadmin);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                throw new ServiceFailure("Solr server error", e.getMessage());
            } 
		}
		return null;
	}
	
	
	/**
	 * Handle the query sent by the http post method
	 * @param session  identity information of the requester
	 * @param engine  the query engine will be used. Now we only support solr
	 * @param params  the query parameters with key/value pairs
	 * @return
	 * @throws InvalidToken
	 * @throws ServiceFailure
	 * @throws NotAuthorized
	 * @throws InvalidRequest
	 * @throws NotImplemented
	 * @throws NotFound
	 */
    public InputStream postQuery(Session session, String engine, HashMap<String, String[]> params) throws InvalidToken,
            ServiceFailure, NotAuthorized, InvalidRequest, NotImplemented, NotFound {
        Set<Subject> subjects = getQuerySubjects(session);
        boolean isMNadmin = isMNAdminQuery(session);
        if (engine != null && engine.equals(EnabledQueryEngines.SOLRENGINE)) {
            if(!EnabledQueryEngines.getInstance().isEnabled(EnabledQueryEngines.SOLRENGINE)) {
                throw new NotImplemented("0000", "MNodeService.query - the query engine "+engine +" hasn't been implemented or has been disabled.");
            }
            try {
                SolrParams solrParams = new MultiMapSolrParams(params);
                return MetacatSolrIndex.getInstance().query(solrParams, subjects, isMNadmin);
            } catch (Exception e) {
                throw new ServiceFailure("2821", "Solr server error: "+ e.getMessage());
            } 
        } else {
            throw new NotImplemented ("2824", "The query engine "+engine+" specified on the request isn't supported by the http post method. Now we only support the solr engine.");
        }
    }
    
    /*
     * Extract all subjects from a given session. If the session is null, the public subject will be returned.
     */
    private Set<Subject> getQuerySubjects(Session session) {
        Set<Subject> subjects = null;
        if (session != null) {
             subjects = AuthUtils.authorizedClientSubjects(session);
        } else {
            //add the public user subject to the set 
            Subject subject = new Subject();
            subject.setValue(Constants.SUBJECT_PUBLIC);
            subjects = new HashSet<Subject>();
            subjects.add(subject);
        }
        return subjects;
    }
    
  /*
   * Determine if the given session is a local admin subject.
   */
    private boolean isMNAdminQuery(Session session) throws ServiceFailure {
        boolean isMNadmin= false;
        if (session != null && session.getSubject() != null) {
            D1AuthHelper authDel = new D1AuthHelper(request, null, "2822", "2821");
            if(authDel.isLocalMNAdmin(session)) {
                logMetacat.debug("MNodeService.query - this is a mn admin session, it will bypass the access control rules.");
                isMNadmin=true;//bypass access rules since it is the admin
            }
        }
        return isMNadmin;
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
	public Identifier publish(Session session, Identifier originalIdentifier) throws InvalidToken, 
	ServiceFailure, NotAuthorized, NotImplemented, InvalidRequest, NotFound, IdentifierNotUnique, 
	UnsupportedType, InsufficientResources, InvalidSystemMetadata, IOException {
		
	    String serviceFailureCode = "1030";
	    Identifier sid = getPIDForSID(originalIdentifier, serviceFailureCode);
	    if(sid != null) {
	        originalIdentifier = sid;
	    }
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
		
		//Get the bytes
		InputStream inputStream = null;		
		boolean isScienceMetadata = isScienceMetadata(sysmeta);
		//If it's a science metadata doc, we want to update the packageId first
		if(isScienceMetadata){
		    boolean isEML = false;
		    //Get the formatId
            ObjectFormatIdentifier objFormatId = originalSystemMetadata.getFormatId();
            String formatId = objFormatId.getValue();
            //For all EML formats
            if(formatId.indexOf("eml") == 0){
                logMetacat.debug("~~~~~~~~~~~~~~~~~~~~~~MNodeService.publish - the object "+originalIdentifier.getValue()+" with format id "+formatId+" is an eml document.");
                isEML = true;
            }
			InputStream originalObject = this.get(session, originalIdentifier);
			
			//Edit the science metadata with the new package Id (EML)
			inputStream = editScienceMetadata(session, originalObject, originalIdentifier, newIdentifier, isEML, sysmeta);
		}
		else{
			inputStream = this.get(session, originalIdentifier);
		}
		
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
				logMetacat.warn("No potential ORE map found for: " + potentialOreIdentifier.getValue()+" by the name convention.");
				// try the SOLR index
				List<Identifier> potentialOreIdentifiers = this.lookupOreFor(session, originalIdentifier);
				if (potentialOreIdentifiers != null && potentialOreIdentifiers.size() >0) {
				    int size = potentialOreIdentifiers.size();
				    for (int i = size-1; i>=0; i--) {
				        Identifier id = potentialOreIdentifiers.get(i);
				        if (id != null && id.getValue() != null && !id.getValue().trim().equals("")) {
				            SystemMetadata sys = this.getSystemMetadata(session, id);
				            if(sys != null && sys.getObsoletedBy() == null) {
				                //found the non-obsoletedBy ore document.
				                logMetacat.debug("MNodeService.publish - found the ore map from the list when the index is " + i);
				                potentialOreIdentifier = id;
				                break;
				            }
				        }
				    }
					try {
						oreInputStream = this.get(session, potentialOreIdentifier);
					} catch (NotFound nf2) {
						// this is probably okay for many sci meta data docs
						logMetacat.warn("No potential ORE map found for: " + potentialOreIdentifier.getValue());
					}
				} else {
				    logMetacat.warn("MNodeService.publish - No potential ORE map found for the metadata object" + originalIdentifier.getValue()+" by both the name convention or the solr query.");
				}
			}
			if (oreInputStream != null) {
			    logMetacat.info("MNodeService.publish - we find the old ore document "+potentialOreIdentifier+" for the metacat object "+originalIdentifier);
				Identifier newOreIdentifier = MNodeService.getInstance(request).generateIdentifier(session, MNodeService.UUID_SCHEME, null);
				ResourceMapModifier modifier = new ResourceMapModifier(potentialOreIdentifier, oreInputStream, newOreIdentifier);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
		        modifier.replaceObsoletedId(originalIdentifier, newIdentifier, out, session.getSubject());
				String resourceMapString = out.toString("UTF-8");
				
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
				oreSysMeta.setFileName("resourceMap_" + newOreIdentifier.getValue() + ".rdf.xml");
				
				// ensure ORE is publicly readable
                oreSysMeta = makePublicIfNot(oreSysMeta, potentialOreIdentifier);
                List<Identifier> dataIdentifiers = modifier.getSubjectsOfDocumentedBy(newIdentifier);
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
				logMetacat.info("MNodeService.publish - the new ore document is "+newOreIdentifier.getValue()+" for the doi "+newIdentifier.getValue());
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
		} catch (NoSuchAlgorithmException e) {
			// report as service failure
			ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
			sf.initCause(e);
			throw sf;
		} catch (SQLException e) {
            // report as service failure
            ServiceFailure sf = new ServiceFailure("1030", e.getMessage());
            sf.initCause(e);
            throw sf;
        }
		
		return newIdentifier;
	}
	
	/**
	   * Update a science metadata document with its new Identifier  
	   * 
	   * @param session - the Session object containing the credentials for the Subject
	   * @param object - the InputStream for the XML object to be edited
	   * @param pid - the Identifier of the XML object to be updated
	   * @param newPid = the new Identifier to give to the modified XML doc
	   * 
	   * @return newObject - The InputStream for the modified XML object
	   * 
	   * @throws ServiceFailure
	   * @throws IOException
	   * @throws UnsupportedEncodingException
	   * @throws InvalidToken
	   * @throws NotAuthorized
	   * @throws NotFound
	   * @throws NotImplemented
	   */
	  public InputStream editScienceMetadata(Session session, InputStream object, Identifier pid, Identifier newPid, boolean isEML, SystemMetadata newSysmeta)
	  	throws ServiceFailure, IOException, UnsupportedEncodingException, InvalidToken, NotAuthorized, NotFound, NotImplemented {
	    
		logMetacat.debug("D1NodeService.editScienceMetadata() called.");
		
		 InputStream newObject = null;
		
	    try{   	
	    	//Get the root node of the XML document
	    	byte[] xmlBytes  = IOUtils.toByteArray(object);
	        String xmlStr = new String(xmlBytes, "UTF-8");
	        
	    	Document doc = XMLUtilities.getXMLReaderAsDOMDocument(new StringReader(xmlStr));
		    org.w3c.dom.Node docNode = doc.getDocumentElement();

	    	//For all EML formats
		    if(isEML){
		        //Update or add the id attribute
		        XMLUtilities.addAttributeNodeToDOMTree(docNode, XPATH_EML_ID, newPid.getValue());
		    }

	        //The modified object InputStream
		    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		    Source xmlSource = new DOMSource(docNode);
		    Result outputTarget = new StreamResult(outputStream);
		    TransformerFactory.newInstance().newTransformer().transform(xmlSource, outputTarget);
		    byte[] output = outputStream.toByteArray();
		    Checksum checksum = ChecksumUtil.checksum(output, newSysmeta.getChecksum().getAlgorithm());
		    newObject = new ByteArrayInputStream(output);
		    newSysmeta.setChecksum(checksum);
		    logMetacat.debug("MNNodeService.editScienceMetadata - the new checksum is "+checksum.getValue() +" with algorithm "+checksum.getAlgorithm()+" for the new pid "+newPid.getValue()+" which is published from the pid "+pid.getValue());
	    } catch(TransformerException e) {
	        throw new ServiceFailure("1030", "MNNodeService.editScienceMetadata(): " +
	                "Could not update the ID in the XML document for " +
	                "pid " + pid.getValue() +" : " + e.getMessage());
	    } catch(IOException e){
	        throw new ServiceFailure("1030", "MNNodeService.editScienceMetadata(): " +
	                "Could not update the ID in the XML document for " +
	                "pid " + pid.getValue() +" : " + e.getMessage());
	    } catch(NoSuchAlgorithmException e) {
	        throw new ServiceFailure("1030", "MNNodeService.editScienceMetadata(): " +
	                "Could not update the ID in the XML document for " +
	                "pid " + pid.getValue() +" since the checksum can't be computed : " + e.getMessage());
	    }
	    
	    return newObject;
	  }
	
	/**
	 * Determines if we already have registered an ORE map for this package
	 * NOTE: uses a solr query to locate OREs for the object
	 * @param guid of the EML/packaging object
	 * @return list of resource map identifiers for the given pid
	 */
	public List<Identifier> lookupOreFor(Session session, Identifier guid, boolean includeObsolete) {
		// Search for the ORE if we can find it
		String pid = guid.getValue();
		List<Identifier> retList = null;
		try {
			String query = "fl=id,resourceMap&wt=xml&q=-obsoletedBy:[* TO *]+resourceMap:[* TO *]+id:\"" + pid + "\"";
			if (includeObsolete) {
				query = "fl=id,resourceMap&wt=xml&q=resourceMap:[* TO *]+id:\"" + pid + "\"";
			}
			
			InputStream results = this.query(session, "solr", query);
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
	
	
	/**
     * Determines if we already have registered an ORE map for this package
     * NOTE: uses a solr query to locate OREs for the object
     * @todo should be consolidate with the above method.
     * @param guid of the EML/packaging object
     */
    private List<Identifier> lookupOreFor(Session session, Identifier guid) {
        // Search for the ORE if we can find it
        String pid = guid.getValue();
        List<Identifier> retList = null;
        try {
            String query = "fl=id,resourceMap&wt=xml&q=id:\"" + pid + "\"";
            InputStream results = this.query(session, "solr", query);
            org.w3c.dom.Node rootNode = XMLUtilities.getXMLReaderAsDOMTreeRootNode(new InputStreamReader(results, "UTF-8"));
            //String resultString = XMLUtilities.getDOMTreeAsString(rootNode);
            org.w3c.dom.NodeList nodeList = XMLUtilities.getNodeListWithXPath(rootNode, "//arr[@name=\"resourceMap\"]/str");
            if (nodeList != null && nodeList.getLength() > 0) {
                retList = new ArrayList<Identifier>();
                for (int i = 0; i < nodeList.getLength(); i++) {
                    String found = nodeList.item(i).getFirstChild().getNodeValue();
                    logMetacat.debug("MNodeService.lookupOreRor - found the resource map"+found);
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

    /*
      * Gets the namespace prefix for the prov ontology.
      *
      * @param resourceMap: The resource map that is being checked
     */
    public String getProvNamespacePrefix(String resourceMap) {
        // Pattern that searches for the text between the last occurance of 'xmlns and
        // the prov namepsace string
        Pattern objectPattern = Pattern.compile("(xmlns:)(?!.*\\1)(.*)(\"http://www.w3.org/ns/prov#\")");
        Matcher m = objectPattern.matcher(resourceMap);
        if (m.find()) {
            // Save the file path for later when it gets written to disk
            return StringUtils.substringBetween(m.group(0), "xmlns:", "=\"http://www.w3.org/ns/prov#\"");
        }
        return "";
    }

    /*
     * Inserts a file table into the readme html. This works by searching for the
     * first table row in the first table group and pasting the table in front of it.
     * If for some reason the readme HTML is empty, just return the table.
     *
     * @param table: The HTML file table
     * @param htmlDoc: The greater HTML document that the table goes in
     */
    public String insertTableIntoReadme(String table, String htmlDoc) {
        if (htmlDoc.length() >1) {
            // Look for the first table within the first table group
            String bodyTag = "<table class=\"subGroup onehundred_percent\">\n";
            int bodyTagLocation = htmlDoc.indexOf(bodyTag);
            // Insert the file table above the table
            int insertLocation = bodyTagLocation+bodyTag.length();
            StringBuilder builder = new StringBuilder(htmlDoc);
            builder.insert(insertLocation, table);
            return builder.toString();
        }
        return table;
    }

    /*
     * Generates a basic readme file using the metacatui xslt.
     *
     * @param session: The current session
     * @param metadataId: The EML metadata ID
     * @param metadataSysMeta: The EML sysmeta document
     */
    public String generateReadmeBody(Session session, Identifier metadataId, SystemMetadata metadataSysMeta) {
        String readmeBody = new String();
        Hashtable<String, String[]> params = new Hashtable<String, String[]>();
        try{
            // Get a stream to the object
            InputStream metadataStream = this.get(session, metadataId);
            // Turn the stream into a string for the xslt
            String documentContent = IOUtils.toString(metadataStream, "UTF-8");
            // Get the type so the xslt can properly parse it
            String sourceType = metadataSysMeta.getFormatId().getValue();
            // Holds the HTML that the transformer returns
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // Handles writing the xslt response to the byte array
            Writer writer = new OutputStreamWriter(baos, "UTF-8");

            DBTransform transformer = new DBTransform();
            transformer.transformXMLDocument(documentContent,
                    sourceType,
                    "-//W3C//HTML//EN",
                    "default",
                    writer,
                    params,
                    null //sessionid
            );
            return baos.toString();
        } catch(Exception e) {
            logMetacat.error("Failed to generate the README body") ;
        }
        return "";
    }

    /**
     * Creates a table of filename, size, and hash for objects in a data package.
     *
     * @param session: The current session
     * @param metadataIds: A list of metadata identifiers of objects that should be included in the table
     * @param coreMetadataIdentifiers: A list of IDs for the eml and resource map
     */
    public String createSysMetaTable(Session session,
                                     List<Identifier> metadataIds,
                                     List<Identifier> coreMetadataIdentifiers) {

        Hashtable<String, String[]> params = new Hashtable<String, String[]>();

        // Holds the HTML rows
        StringBuilder tableHTMLBuilder = new StringBuilder();
        for(Identifier metadataID: metadataIds) {
            // Check to make sure the object is a data file that the user had uploaded.
            if(coreMetadataIdentifiers.contains(metadataID)) {
                continue;
            }
            try {
                SystemMetadata entrySysMeta = this.getSystemMetadata(session, metadataID);
                Identifier objectSystemMetadataID = entrySysMeta.getIdentifier();

                // Holds the content of the system metadata document
                ByteArrayOutputStream sysMetaStream = new ByteArrayOutputStream();

                // Retrieve the sys meta document
                TypeMarshaller.marshalTypeToOutputStream(entrySysMeta, sysMetaStream);

                // Holds the HTML that the xslt returns
                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                // Handles writing the xslt response to the byte array
                Writer writer = new OutputStreamWriter(baos, "UTF-8");

                // The xslt requires the sysmeta document in string form
                String documentContent = sysMetaStream.toString("UTF-8");
                DBTransform transformer = new DBTransform();

                // Transform the system metadocument using the 'export' xslt, targeting an HTML return type
                transformer.transformXMLDocument(documentContent,
                        "package-export",
                        "-//W3C//HTML//EN",
                        "package-export",
                        writer,
                        params,
                        null //sessionid
                );

                // Write the table to our string builder
                tableHTMLBuilder.append(baos.toString());
            } catch (Exception e) {
                logMetacat.error("There was an error while generating the README table.");
            }
        }

        // This is ugly, but we need to wrap the sysmeta file rows in a table
        String openingHtml ="<td class=\"fortyfive_percent\"><table class=\"subGroup subGroup_border onehundred_percent\">"+
                "<tr>"+
                "<th colspan=\"3\">"+
                "<h4>Files in this downloaded dataset:</h4>"+
                "</tr>"+
                "<tr>"+
                "<TD><B>Name</B></TD>"+
                "<TD><B>Size (Bytes)</B></TD>"+
                "</tr>"+
                "</th>";
        String closingHtml = "</table></td>";

        // Wrap the table rows in a table
        return openingHtml + tableHTMLBuilder.toString() + closingHtml;
    }

    /*
     * Generates the README.html file in the bag root. This is a partially hacky
     * portion of code. To do this, a file table is generated via an xslt and system metadata
     * documents. Then, the metacatui theme is used to generate a complete readme document. The
     * table is then inserted into the html body.
     *
     * @param session: The current session
     * @param metadataIds: A list of metadata identifiers that represent objects in the package
     * @param coreMetadataIdentifiers: A list of metadata IDs for the EML and resource map
     * @param tempBagRoot: The temporary directory where the unzipped-bag is being stored
     */
    public File generateReadmeHTMLFile(Session session,
                                       List<Identifier> metadataIds,
                                       List<Identifier> coreMetadataIdentifiers,
                                       File tempBagRoot) {
        String readmeBody = "";
        File ReadmeFile = null;
        // Generate the file table
        String sysmetaTable = this.createSysMetaTable(session, metadataIds, coreMetadataIdentifiers);

        try {
            // The body of the README is generated from the primary system metadata document. Find this document, and
            // generate the body.
            for (Identifier metadataID : metadataIds) {
                SystemMetadata metadataSysMeta = this.getSystemMetadata(session, metadataID);
                // Look for the science metadata object
                if (ObjectFormatCache.getInstance().getFormat(metadataSysMeta.getFormatId()).getFormatType().equals("METADATA")) {
                    readmeBody = this.generateReadmeBody(session, metadataID, metadataSysMeta);
                }
            }
            // Now that we have the HTML table and the rest of the HTML body, we need to combine them
            String fullHTML = this.insertTableIntoReadme(sysmetaTable, readmeBody);
            ReadmeFile = new File(tempBagRoot.getAbsolutePath() + "/" + "README.html");
            // Write the html to a stream
            ContentTypeByteArrayInputStream resultInputStream = new ContentTypeByteArrayInputStream(fullHTML.getBytes());
            // Copy the bytes to the html file
            IOUtils.copy(resultInputStream, new FileOutputStream(ReadmeFile, true));
        } catch (Exception e) {
            logMetacat.error("There was an error while writing README.html", e);
        }
        return ReadmeFile;
    }

    /*
     * Creates a bag file from a directory and returns a stream to it.
     * @param bagFactory And instance of BagIt.BagFactory
     * @param bag The bag instance being used for this export
     * @param streamedBagFile The folder that holds the data being exported
     * @param dataRoot The root data folder
     * @param metadataRoot The root metadata directory
     * @param pid The package pid
     */
    private InputStream createExportBagStream(BagFactory bagFactory,
                                              Bag bag,
                                              File streamedBagFile,
                                              File dataRoot,
                                              File metadataRoot,
                                              Identifier pid) {
        InputStream bagInputStream = null;
        String bagName = pid.getValue().replaceAll("\\W", "_");
        try {
            File[] files = dataRoot.listFiles();
            for (File fle : files) {
                bag.addFileToPayload(fle);
            }
            bag.addFileAsTag(metadataRoot);
            File bagFile = new File(streamedBagFile, bagName + ".zip");
            bag.setFile(bagFile);
            bag = bag.makeComplete();
            ZipWriter zipWriter = new ZipWriter(bagFactory);
            bag.write(zipWriter, bagFile);
            // Make sure the bagFile is current
            bagFile = bag.getFile();
            // use custom FIS that will delete the file when closed
            bagInputStream = new DeleteOnCloseFileInputStream(bagFile);
            // also mark for deletion on shutdown in case the stream is never closed
            bagFile.deleteOnExit();
        } catch (Exception e) {
            logMetacat.error("There was an error creating the bag file.");
        }
        return bagInputStream;
    }

    /*
     * Writes an EML document to disk. In particular, it writes it to the metadata/ directory which eventually
     * gets added to the bag.
     *
     * @param session: The current session
     * @param metadataID: The ID of the EML document
     * @param metadataRoot: The File that represents the metadata/ folder

     */
    private void writeEMLDocument(Session session,
                                  Identifier metadataID,
                                  File metadataRoot) {

        InputStream emlStream = null;
        try {
            emlStream = this.get(session, metadataID);
        } catch (Exception e) {
            logMetacat.error("Failed to retrieve the EML document.", e);
        }
        try {
            // Write the EML document to the bag zip
            String documentContent = IOUtils.toString(emlStream, "UTF-8");
            String filename = metadataRoot.getAbsolutePath() + "/" + "eml.xml";
            File systemMetadataDocument = new File(filename);
            BufferedWriter writer = new BufferedWriter(new FileWriter(systemMetadataDocument));
            writer.write(documentContent);
            writer.close();
        }
        catch (Exception e) {
            logMetacat.warn("Failed to write the eml document.", e);
        }
    }

    /*
     * Searches through the resource map for any objects that have had their location specified with
     * prov:atLocation. The filePathMap parameter is mutated with the file path and corresponding pid.
     *
     * @param resMap: The resource map that's being parsed
     * @param filePathMap: Mapping between pid and file path. Should be empty when passed in

     */
    private void documentObjectLocations(ResourceMap resMap,
                                         Map<String, String> filePathMap)
    {
        try {
            String resMapString = ResourceMapFactory.getInstance().serializeResourceMap(resMap);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(resMapString)));

            // Attempt to search for any atLocation references
            String atLocationPrefix = getProvNamespacePrefix(resMapString);
            if(atLocationPrefix.length()>0) {
                org.w3c.dom.NodeList nodeList = document.getElementsByTagName(atLocationPrefix + ":atLocation");

                // For each atLocation record, we want to save the location and the pid of the object
                for (int i = 0; i < nodeList.getLength(); i++) {
                    org.w3c.dom.Node node = nodeList.item(i);
                    org.w3c.dom.NamedNodeMap parentAttributes = node.getParentNode().getAttributes();
                    String parentURI = parentAttributes.item(0).getTextContent();
                    logMetacat.info(parentURI);
                    String filePath = node.getTextContent();
                    filePath = filePath.replaceAll("\"", "");

                    // We're given the full URI of the object, but we only want the PID at the end
                    Pattern objectPattern = Pattern.compile("(?<=object/).*(?)");
                    Matcher m = objectPattern.matcher(parentURI);
                    if (m.find()) {
                        // Save the file path for later when it gets written to disk
                        filePathMap.put(m.group(0), filePath);
                    } else {
                        objectPattern = Pattern.compile("(?<=resolve/).*(?)");
                        m = objectPattern.matcher(parentURI);
                        if (m.find()) {
                            // Save the file path for later when it gets written to disk
                            filePathMap.put(m.group(0), filePath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logMetacat.warn("There was an exception while searching for prov:atLocation.", e);
        }
    }

    /*
     * Writes the resource map to disk. This file gets written to the metadata/ folder.
     *
     * @param resMap: The resource map that's being written to disk
     * @param metadataRoot: The File that represents the metadata/ folder
     *
     */
    private void writeResourceMap(ResourceMap resMap,
                                  File metadataRoot) {

        // Write the resource map to the metadata directory
        String filename = metadataRoot.getAbsolutePath() + "/" + "oai-ore.xml";
        try {
            String resMapString = ResourceMapFactory.getInstance().serializeResourceMap(resMap);
            File systemMetadataDocument = new File(filename);
            BufferedWriter writer = new BufferedWriter(new FileWriter(systemMetadataDocument));
            writer.write(resMapString);
            writer.close();
        }
        catch (IOException e) {
            logMetacat.error("Failed to write resource map to the bag.", e);
        } catch (ORESerialiserException e) {
            logMetacat.error("Failed to de-serialize the resource map", e);
        }
    }

    /*
     * Checks to see if the package can be exported based on the formatId
     *
     * @param formatId: The format ID of the package
     * @param pid: The pid of the package
     *
     * @throws InvalidRequest
     * @throws NotImplemented
     * @throws ServiceFailure

     */
    private void validateExportPackage(ObjectFormatIdentifier formatId, Identifier pid)
            throws InvalidRequest, NotImplemented, ServiceFailure{
        if(formatId == null) {
            throw new InvalidRequest("2873", "The format type can't be null in the getpackage method.");
        } else if(!formatId.getValue().equals("application/bagit-097")) {
            throw new NotImplemented("", "The format "+formatId.getValue()+" is not supported in the getpackage method");
        }

        String serviceFailureCode = "2871";
        Identifier sid = getPIDForSID(pid, serviceFailureCode);
        if(sid != null) {
            pid = sid;
        }
    }

	@Override
	public InputStream getPackage(Session session, ObjectFormatIdentifier formatId,
			Identifier pid) throws InvalidToken, ServiceFailure,
			NotAuthorized, InvalidRequest, NotImplemented, NotFound {

        // Do some checks to make sure this package can be exported
        validateExportPackage(formatId, pid);

		// track the temp files we use so we can delete them when finished
		List<File> tempFiles = new ArrayList<File>();

        // Holds any science metadata and resource map pids
        List<Identifier> coreMetadataIdentifiers = new ArrayList<Identifier>();
        coreMetadataIdentifiers.add(pid);

        // Map of objects to filepaths
        Map<String, String> filePathMap = new HashMap<String, String>();

		// the pids to include in the package
		List<Identifier> packagePids = new ArrayList<Identifier>();

		// Container that holds the pids of all of the objects that are in a package
        List<Identifier> pidsOfPackageObjects = new ArrayList<Identifier>();

        /*
           The bag has a few standard directories (metadata, metadata/sysmeta, data/). Create File objects
           representing each of these directories so that the appropriate files can be added to them. Initialize them
           to null so that they can be used outside of the try/catch block.
         */

        // A temporary directory where the non-zipped bag is formed
        File tempBagRoot = null;

        // A temporary direcotry within the tempBagRoot that represents the metadata/ direcrory
        File metadataRoot = null;
        // A temporary directory within metadataRoot that holds system metadata
        File systemMetadataDirectory = null;
        // A temporary directory within tempBagRoot that holds data objects
        File dataRoot = null;

        // Tie the File objects above to actual locations on the filesystem
        try {
            tempBagRoot = new File("/var/tmp/exportedPackages/"+Long.toString(System.nanoTime()));
            tempBagRoot.mkdirs();

            metadataRoot = new File(tempBagRoot.getAbsolutePath() + "/metadata");
            metadataRoot.mkdir();

            systemMetadataDirectory = new File(metadataRoot.getAbsolutePath() + "/sysmeta");
            systemMetadataDirectory.mkdir();

            dataRoot = new File(tempBagRoot.getAbsolutePath() + "/data");
            dataRoot.mkdir();
        } catch (Exception e) {
            logMetacat.warn("Error creating bag files", e);
            throw new ServiceFailure("", "Metacat failed to create the temporary bag archive.");
        }

        // Get the system metadata for the package
        SystemMetadata sysMeta = this.getSystemMetadata(session, pid);
        ResourceMap resMap = null;

        // Maps a resource map to a list of aggregated identifiers. Use this to get the list of pids inside
        Map<Identifier, Map<Identifier, List<Identifier>>> resourceMapStructure = null;

        if (ObjectFormatCache.getInstance().getFormat(sysMeta.getFormatId()).getFormatType().equals("RESOURCE")) {
            InputStream oreInputStream = null;

            // Attempt to open/parse the resource map so that we can get a list of pids inside
            try {
                oreInputStream = this.get(session, pid);
                resMap = ResourceMapFactory.getInstance().deserializeResourceMap(oreInputStream);
                // Check for prov:atLocation and save them in filePathMap
                this.documentObjectLocations(resMap, filePathMap);
                // Write the resource map to disk
                this.writeResourceMap(resMap, metadataRoot);

                oreInputStream = this.get(session, pid);
                resourceMapStructure = ResourceMapFactory.getInstance().parseResourceMap(oreInputStream);
                pidsOfPackageObjects.addAll(resourceMapStructure.keySet());
            } catch (Exception e) {
                logMetacat.error("Error getting identifiers from the resource map.", e);
            }
        } else {
            //throw an invalid request exception if there's just a single pid
            throw new InvalidRequest("2873", "The given pid " + pid.getValue() + " is not a package id (resource map id). Please use a package id instead.");
        }

        // Use the list of pids to gather and store information about each corresponding sysmeta document
        try {
            for (Map<Identifier, List<Identifier>> entries : resourceMapStructure.values()) {
                Set<Identifier> metadataIdentifiers = entries.keySet();

                pidsOfPackageObjects.addAll(entries.keySet());
                for (List<Identifier> dataPids : entries.values()) {
                    pidsOfPackageObjects.addAll(dataPids);
                }

                // Loop over each metadata document
                for (Identifier metadataID : metadataIdentifiers) {
                    //Get the system metadata for this metadata object
                    SystemMetadata metadataSysMeta = this.getSystemMetadata(session, metadataID);
                    // Handle any system metadata
                    if (ObjectFormatCache.getInstance().getFormat(metadataSysMeta.getFormatId()).getFormatType().equals("METADATA")) {
                        //If this is in eml format, write it to the temporary bag metadata directory
                        String metadataType = metadataSysMeta.getFormatId().getValue();
                        if (metadataType.startsWith("eml://") || metadataSysMeta.getFormatId().getValue().startsWith("https://eml.ecoinformatics.org")) {
                            // Add the ID to the list of metadata pids
                            coreMetadataIdentifiers.add(metadataID);

                            // Write the EML document to disk
                            this.writeEMLDocument(session,
                                    metadataID,
                                    metadataRoot);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logMetacat.warn("There was an error while parsing the files in the resource map.", e);
        }

        List<Identifier> metadataIds = new ArrayList();
        /*
		    Loop over each pid in the resource map. First, the system metadata gets written. Next, the data file that
			corresponds to the system metadata gets written.
		*/

        // loop through the package contents
        for (Identifier entryPid : pidsOfPackageObjects) {
            //Get the system metadata for the objbect with pid entryPid
            SystemMetadata entrySysMeta = this.getSystemMetadata(session, entryPid);

            // Write its system metadata to disk
            Identifier objectSystemMetadataID = entrySysMeta.getIdentifier();
            metadataIds.add(objectSystemMetadataID);
            try {
                String filename = systemMetadataDirectory.getAbsolutePath() + "/sysmeta-" + objectSystemMetadataID.getValue() + ".xml";
                File systemMetadataDocument = new File(filename);
                FileOutputStream sysMetaStream = new FileOutputStream(systemMetadataDocument);
                TypeMarshaller.marshalTypeToOutputStream(entrySysMeta, sysMetaStream);
            } catch (Exception e) {
                logMetacat.warn("Error writing system metadata to disk.");
            }

            // Skip the resource map and the science metadata so that we don't write them to the data direcotry
            if (coreMetadataIdentifiers.contains(entryPid)) {
                continue;
            }

            String objectFormatType = ObjectFormatCache.getInstance().getFormat(entrySysMeta.getFormatId()).getFormatType();
            String fileName = null;

            //TODO: Be more specific of what characters to replace. Make sure periods arent replaced for the filename from metadata
            //Our default file name is just the ID + format type (e.g. walker.1.1-DATA)
            fileName = entryPid.getValue().replaceAll("[^a-zA-Z0-9\\-\\.]", "_") + "-" + objectFormatType;

            // ensure there is a file extension for the object
            String extension = ObjectFormatInfo.instance().getExtension(entrySysMeta.getFormatId().getValue());
            fileName += extension;

            // if SM has the file name, ignore everything else and use that
            if (entrySysMeta.getFileName() != null) {
                fileName = entrySysMeta.getFileName().replaceAll("[^a-zA-Z0-9\\-\\.]", "_");
            }

            // Create a new file for this data file. The default location is in the data diretory
            File dataPath = dataRoot;

            // Create the directory for the data file, if it was specified in the resource map
            if (filePathMap.containsKey(entryPid.getValue())) {
                dataPath = new File(dataRoot.getAbsolutePath() + "/" + filePathMap.get(entryPid.getValue()));
            }
            // We want to make sure that the directory exists before referencing it later
            if (!dataPath.exists()) {
                dataPath.mkdirs();
            }

            // Create a temporary file that will hold the bytes of the data object. This file will get
            // placed into the bag
            File tempFile = new File(dataPath, fileName);
            try {
                InputStream entryInputStream = this.get(session, entryPid);
                IOUtils.copy(entryInputStream, new FileOutputStream(tempFile));
            } catch (Exception e) {
                logMetacat.warn("There was an error while writing a data file", e);
            }
        }

        BagFactory bagFactory = new BagFactory();
        Bag bag = bagFactory.createBag();

        // Create the README.html document
        File ReadmeFile = this.generateReadmeHTMLFile(session,
                metadataIds,
                coreMetadataIdentifiers,
                tempBagRoot);
        bag.addFileAsTag(ReadmeFile);

        // The directory where the actual bag zipfile is saved (and streamed from)
        File streamedBagFile = new File("/var/tmp/exportedPackages/"+Long.toString(System.nanoTime()));

        InputStream bagStream = createExportBagStream(bagFactory,
                bag,
                streamedBagFile,
                dataRoot,
                metadataRoot,
                pid);
        try {
            FileUtils.deleteDirectory(tempBagRoot);
            FileUtils.deleteDirectory(streamedBagFile);
        } catch (IOException e) {
            logMetacat.error("There was an error deleting the bag artifacts.", e);
        }
        return bagStream;
    }
	
	 /**
	   * Archives an object, where the object is either a 
	   * data object or a science metadata object.
	   * 
	   * @param session - the Session object containing the credentials for the Subject
	   * @param pid - The object identifier to be archived
	   * 
	   * @return pid - the identifier of the object used for the archiving
	   * 
	   * @throws InvalidToken
	   * @throws ServiceFailure
	   * @throws NotAuthorized
	   * @throws NotFound
	   * @throws NotImplemented
	   * @throws InvalidRequest
	   */
	  public Identifier archive(Session session, Identifier pid) 
	      throws InvalidToken, ServiceFailure, NotAuthorized, NotFound, NotImplemented 
	  {
	      if(isReadOnlyMode()) {
	          throw new ServiceFailure("2912", ReadOnlyChecker.DATAONEERROR);
	      }
	      boolean allowed = false;
	      // do we have a valid pid?
	      if (pid == null || pid.getValue().trim().equals("")) {
	          throw new ServiceFailure("1350", "The provided identifier was invalid.");
	      }

	      String serviceFailureCode = "1350";
	      Identifier sid = getPIDForSID(pid, serviceFailureCode);
	      if(sid != null) {
	          pid = sid;
	      }
	      // does the subject have archive (a D1 CHANGE_PERMISSION level) privileges on the pid?
	      try {
	          allowed = isAuthorized(session, pid, Permission.CHANGE_PERMISSION);
	      } catch (InvalidRequest e) {
	          throw new ServiceFailure("1350", e.getDescription());
	      } 

	      if (allowed) {
	          try {
	              HazelcastService.getInstance().getSystemMetadataMap().lock(pid);
	              logMetacat.debug("MNodeService.archive - lock the identifier "+pid.getValue()+" in the system metadata map.");
	              SystemMetadata sysmeta = HazelcastService.getInstance().getSystemMetadataMap().get(pid);
	              boolean needModifyDate = true;
	              boolean logArchive = true;
	              super.archiveObject(logArchive, session, pid, sysmeta, needModifyDate); 
	          } finally {
	              HazelcastService.getInstance().getSystemMetadataMap().unlock(pid);
	              logMetacat.debug("MNodeService.archive - unlock the identifier "+pid.getValue()+" in the system metadata map.");
	          }


	      } else {
	          throw new NotAuthorized("1320", "The provided identity does not have " + "permission to archive the object on the Node.");
	      }

	      return pid;
	  }
    
	  /**
	   * Update the system metadata of the specified pid.
	   */
	  @Override
	  public boolean updateSystemMetadata(Session session, Identifier pid,
	          SystemMetadata sysmeta) throws NotImplemented, NotAuthorized,
	  ServiceFailure, InvalidRequest, InvalidSystemMetadata, InvalidToken {

	      if(isReadOnlyMode()) {
	          throw new ServiceFailure("4868",  ReadOnlyChecker.DATAONEERROR);
	      }
	      if(sysmeta == null) {
	          throw  new InvalidRequest("4869", "The system metadata object should NOT be null in the updateSystemMetadata request.");
	      }
	      if(pid == null || pid.getValue() == null) {
	          throw new InvalidRequest("4869", "Please specify the id in the updateSystemMetadata request ") ;
	      }

	      if (session == null) {
	          //TODO: many of the thrown exceptions do not use the correct error codes
	          //check these against the docs and correct them
	          throw new NotAuthorized("4861", "No Session - could not authorize for updating system metadata." +
	                  "  If you are not logged in, please do so and retry the request.");
	      } 
	      //update the system metadata locally
	      boolean success = false;
	      try {
	          HazelcastService.getInstance().getSystemMetadataMap().lock(pid);
	          SystemMetadata currentSysmeta = HazelcastService.getInstance().getSystemMetadataMap().get(pid);
	          if(currentSysmeta == null) {
	              throw  new InvalidRequest("4869", "We can't find the current system metadata on the member node for the id "+pid.getValue());
	          }
	          try {
                  D1AuthHelper authDel = new D1AuthHelper(request, pid, "4861","4868");
                  authDel.doUpdateAuth(session, currentSysmeta, Permission.CHANGE_PERMISSION, this.getCurrentNodeId());
              } catch(ServiceFailure e) {
                  throw new ServiceFailure("4868", "Can't determine if the client has the permission to update the system metacat of the object with id "+pid.getValue()+" since "+e.getDescription());
              } catch(NotAuthorized e) {
                  throw new NotAuthorized("4861", "Can't update the system metacat of the object with id "+pid.getValue()+" since "+e.getDescription());
              }      
	          Date currentModiDate = currentSysmeta.getDateSysMetadataModified();
	          Date commingModiDate = sysmeta.getDateSysMetadataModified();
	          if(commingModiDate == null) {
	              throw  new InvalidRequest("4869", "The system metadata modification date can't be null.");
	          }
	          if(currentModiDate != null && commingModiDate.getTime() != currentModiDate.getTime()) {
	              throw new InvalidRequest("4869", "Your system metadata modification date is "+commingModiDate.toString()+
	                      ". It doesn't match our current system metadata modification date in the member node - "+currentModiDate.toString()+
	                      ". Please check if you have got the newest version of the system metadata before the modification.");
	          }
	          //check the if client change the authoritative member node.
	          if (currentSysmeta.getAuthoritativeMemberNode() != null && sysmeta.getAuthoritativeMemberNode() != null && 
	                  !currentSysmeta.getAuthoritativeMemberNode().equals(sysmeta.getAuthoritativeMemberNode())) {
	              throw new InvalidRequest("4869", "Current authoriativeMemberNode is "+currentSysmeta.getAuthoritativeMemberNode().getValue()+" but the value on the new system metadata is "+sysmeta.getAuthoritativeMemberNode().getValue()+
	                      ". They don't match. Clients don't have the permission to change it.");
	          } else if (currentSysmeta.getAuthoritativeMemberNode() != null && sysmeta.getAuthoritativeMemberNode() == null) {
	              throw new InvalidRequest("4869", "Current authoriativeMemberNode is "+currentSysmeta.getAuthoritativeMemberNode().getValue()+" but the value on the new system metadata is null. They don't match. Clients don't have the permission to change it.");
	          }
	          else if(currentSysmeta.getAuthoritativeMemberNode() == null && sysmeta.getAuthoritativeMemberNode() != null ) {
	              throw new InvalidRequest("4869", "Current authoriativeMemberNode is null but the value on the new system metadata is not null. They don't match. Clients don't have the permission to change it.");
	          }
	          checkAddRestrictiveAccessOnDOI(currentSysmeta, sysmeta);
	          boolean needUpdateModificationDate = true;
	          boolean fromCN = false;
	          success = updateSystemMetadata(session, pid, sysmeta, needUpdateModificationDate, currentSysmeta, fromCN);
	      } finally {
	          HazelcastService.getInstance().getSystemMetadataMap().unlock(pid);
	      }

	      if(success && needSync) {
	          logMetacat.debug("MNodeService.updateSystemMetadata - the cn needs to be notified that the system metadata of object " +pid.getValue()+" has been changed ");
	          this.cn = D1Client.getCN();
	          //TODO
	          //notify the cns the synchornize the new system metadata.
	          // run it in a thread to avoid connection timeout
	          Runnable runner = new Runnable() {
	              private CNode cNode = null;
	              private SystemMetadata sys = null;
	              private Identifier id = null;
	              @Override
	              public void run() {
	                  try {
	                      if(this.cNode == null)  {
	                          logMetacat.warn("MNodeService.updateSystemMetadata - can't get the instance of the CN. So can't call cn.synchronize to update the system metadata in CN.");
	                      } else if(id != null) {
	                          logMetacat.info("MNodeService.updateSystemMetadata - calling cn.synchornized in another thread for pid "+id.getValue());
	                          this.cNode.synchronize(null, id);
	                      } else {
	                          logMetacat.warn("MNodeService.updateSystemMetadata - the pid is null. So can't call cn.synchronize to update the system metadata in CN.");
	                      }
	                  } catch (BaseException e) {
	                      e.printStackTrace();
	                      logMetacat.error("It is a DataONEBaseException and its detail code is "+e.getDetail_code() +" and its code is "+e.getCode());
	                      logMetacat.error("Can't update the systemmetadata of pid "+id.getValue()+" in CNs through cn.synchronize method since "+e.getMessage(), e);
	                  } catch (Exception e) {
	                      e.printStackTrace();
	                      logMetacat.error("Can't update the systemmetadata of pid "+id.getValue()+" in CNs through cn.synchronize method since "+e.getMessage(), e);
	                  }

	                  // attempt to re-register the identifier (it checks if it is a doi)
	                  try {
	                      logMetacat.info("MNodeSerice.updateSystemMetadata - register doi if the pid "+sys.getIdentifier().getValue()+" is a doi");
	                      DOIService.getInstance().registerDOI(sys);
	                  } catch (Exception e) {
	                      logMetacat.warn("Could not [re]register DOI: " + e.getMessage(), e);
	                  }
	              }
	              private Runnable init(CNode cn, SystemMetadata sys, Identifier id){
	                  this.cNode = cn;
	                  this.sys = sys;
	                  this.id = id;
	                  return this;

	              }
	          }.init(cn, sysmeta, pid);
	          // submit the task, and that's it
	          if(executor != null) {
	              executor.submit(runner);
	          } else {
	              logMetacat.warn("MNodeSerivce.updateSystemMetadata - since the executor service for submitting the call of cn.synchronize() is null, the system metadata change of the id "+pid.getValue()+" can't go to cn through cn.synchronize.");
	          }
	      }
	      return success;
	  }

	  /**
	   * Get the status of the system. this is an unofficial dataone api method. Currently we only reply the size of the index queue.
	   * The method will return the input stream of a xml instance. In the future, we need to
	   * add a new dataone type to represent the result.
	   * @param session
	   * @return the input stream which is the xml presentation of the status report
	   */
	  public InputStream getStatus(Session session) throws NotAuthorized, ServiceFailure {
	      int size = HazelcastService.getInstance().getIndexQueue().size();
	      StringBuffer result = new StringBuffer();
	      result.append("<?xml version=\"1.0\"?>");
	      result.append("<status>");
	      result.append("<index>");
	      result.append("<sizeOfQueue>");
	      result.append(size);
	      result.append("</sizeOfQueue>");
	      result.append("</index>");
	      result.append("</status>");
	      return IOUtils.toInputStream(result.toString());
	  }
	
	/**
	 * Check if the new system meta data removed the public-readable access rule for an DOI object ( DOI can be in the identifier or sid fields)
	 * @param newSysMeta
	 * @throws InvalidRequest
	 */
	private void checkAddRestrictiveAccessOnDOI(SystemMetadata oldSysMeta, SystemMetadata newSysMeta)  throws InvalidRequest {
	    String doi ="doi:";
	    boolean identifierIsDOI = false;
        boolean sidIsDOI = false;
        if(newSysMeta.getIdentifier() == null) {
            throw new InvalidRequest("4869", "In the MN.updateSystemMetadata method, the identifier shouldn't be null in the new version system metadata ");
        }
        String identifier = newSysMeta.getIdentifier().getValue();
        String sid = null;
        if(newSysMeta.getSeriesId() != null) {
            sid = newSysMeta.getSeriesId().getValue();
        }
        // determine if this identifier is an DOI
        if (identifier != null && identifier.startsWith(doi)) {
            identifierIsDOI = true;
        }
        // determine if this sid is an DOI
        if (sid != null && sid.startsWith(doi)) {
            sidIsDOI = true;
        }
        if(identifierIsDOI || sidIsDOI) {
            Subject publicUser = new Subject();
            publicUser.setValue("public");
            //We only apply this rule when the old system metadata allow the public user read this object.
            boolean isOldSysmetaPublicReadable = false;
            AccessPolicy oldAccess = oldSysMeta.getAccessPolicy();
            if(oldAccess != null) {
                if (oldAccess.getAllowList() != null) {
                    for (AccessRule item : oldAccess.getAllowList()) {
                        if(item.getSubjectList() != null && item.getSubjectList().contains(publicUser)) {
                            if (item.getPermissionList() != null && item.getPermissionList().contains(Permission.READ)) {
                                isOldSysmetaPublicReadable = true;
                                break;
                            }
                        }
                    }
                }
            }
            if(isOldSysmetaPublicReadable) {
                AccessPolicy access = newSysMeta.getAccessPolicy();
                if(access == null) {
                    throw new InvalidRequest("4869", "In the MN.updateSystemMetadata method, the public-readable access rule shouldn't be removed for an DOI object "+identifier+ " or SID "+sid);
                } else {
                    boolean found = false;
                    if (access.getAllowList() != null) {
                        for (AccessRule item : access.getAllowList()) {
                            if(item.getSubjectList() != null && item.getSubjectList().contains(publicUser)) {
                                if (item.getPermissionList() != null && item.getPermissionList().contains(Permission.READ)) {
                                    found = true;
                                    break;
                                }
                            }
                        }
                    }
                    if(!found) {
                        throw new InvalidRequest("4869", "In the MN.updateSystemMetadata method, the public-readable access rule shouldn't be removed for an DOI object "+identifier+ " or SID "+sid);
                    }
                }
            }
        }
	}
	
	protected NodeReference getCurrentNodeId() {
	    return TypeFactory.buildNodeReference(Settings.getConfiguration().getString("dataone.nodeId"));
	}
	
	/**
     * Determine if the current node is the authoritative node for the given pid.
     * (uses HZsysmeta map)
     */
    protected boolean isAuthoritativeNode(Identifier pid) throws InvalidRequest {
        boolean isAuthoritativeNode = false;
        if(pid != null && pid.getValue() != null) {
            SystemMetadata sys = HazelcastService.getInstance().getSystemMetadataMap().get(pid);
            if(sys != null) {
                NodeReference node = sys.getAuthoritativeMemberNode();
                if(node != null) {
                    String nodeValue = node.getValue();
                    logMetacat.debug("The authoritative node for id "+pid.getValue()+" is "+nodeValue);
                    String currentNodeId = Settings.getConfiguration().getString("dataone.nodeId");
                    logMetacat.debug("The node id in metacat.properties is "+currentNodeId);
                    if(currentNodeId != null && !currentNodeId.trim().equals("") && currentNodeId.equals(nodeValue)) {
                        logMetacat.debug("They are matching, so the authoritative mn of the object "+pid.getValue()+" is the current node");
                        isAuthoritativeNode = true;
                    }
                } else {
                    throw new InvalidRequest("4869", "Coudn't find the authoritative member node in the system metadata associated with the pid "+pid.getValue());
                }
            } else {
                throw new InvalidRequest("4869", "Coudn't find the system metadata associated with the pid "+pid.getValue());
            }
        } else {
            throw new InvalidRequest("4869", "The request pid is null");
        }
        return isAuthoritativeNode;
    }
    
    
    /**
     * Check if the metacat is in the read-only mode.
     * @return true if it is; otherwise false.
     */
    protected boolean isReadOnlyMode() {
        boolean readOnly = false;
        ReadOnlyChecker checker = new ReadOnlyChecker();
        readOnly = checker.isReadOnly();
        return readOnly;
    }
    
}
