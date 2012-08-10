/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007 Sun Microsystems Inc. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * https://opensso.dev.java.net/public/CDDLv1.0.html or
 * opensso/legal/CDDLv1.0.txt
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at opensso/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * $Id: SMProfileModelImpl.java,v 1.5 2008/06/25 05:43:21 qcheng Exp $
 *
 */

/*
 * Portions Copyrighted 2011 ForgeRock AS
 */

package com.sun.identity.console.session.model;

import com.iplanet.dpro.session.Session;
import com.iplanet.dpro.session.SessionException;
import com.iplanet.dpro.session.SessionID;
import com.iplanet.sso.SSOException;
import com.iplanet.sso.SSOToken;
import com.sun.identity.common.SearchResults;
import com.sun.identity.common.configuration.ServerConfiguration;
import com.sun.identity.console.base.model.AMAdminConstants;
import com.sun.identity.console.base.model.AMAdminUtils;
import com.sun.identity.console.base.model.AMConsoleException;
import com.sun.identity.console.base.model.AMModelBase;
import com.sun.identity.console.delegation.model.DelegationConfig;
import com.sun.identity.console.property.MultiServicesPropertyXMLBuilder;
import com.sun.identity.console.property.PropertyTemplate;
import com.sun.identity.console.property.PropertyXMLBuilderBase;
import com.sun.identity.security.AdminTokenAction;
import com.sun.identity.sm.AttributeSchema;
import com.sun.identity.sm.OrganizationConfigManager;
import com.sun.identity.sm.SMSException;
import com.sun.identity.sm.ServiceSchema;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;

/* - LOG COMPLETE - */

/**
 * Class that provides methods to manage Session Management.
 */
public class SMProfileModelImpl extends AMModelBase
    implements SMProfileModel
{
    private static SSOToken adminSSOToken =
            (SSOToken) AccessController.doPrivileged(AdminTokenAction.getInstance());

    private final static String SERVER_LIST = 
        "iplanet-am-platform-server-list";

    public  final static String USER_ID = "UserId";

    private SMSessionCache sessionCache;
    private static String DELIMITER = "|";
    private Map serverNamesMap = null;
    private String serverName = null;
    private boolean validSession = true;
    
    /**
     * Constructs a session management profile model implementation object.
     *
     * @param request  HTTP Servlet request.
     * @param map of user information.
     */
    public SMProfileModelImpl(HttpServletRequest request, Map map) {
        super(request, map);
    }

    /**
     * Sets server name.
     *
     * @param value sever name.
     */
    public void setProfileServerName(String value) {
        serverName = value;
    }

    /**
     * Returns sub realm creation property XML.
     *
     * @throws AMConsoleException if there are no attributes to display.
     * @return sub realm creation property XML.
     */
    public String getCreateRealmPropertyXML()
            throws AMConsoleException {
        StringBuffer buff = new StringBuffer(2000);
        buff.append(PropertyXMLBuilderBase.getXMLDefinitionHeader())
                .append(PropertyTemplate.START_TAG);
        getPropertyXML(buff, false);
        buff.append(PropertyTemplate.END_TAG);
        return buff.toString();
    }

    /**
     * Returns realm profile property XML.
     *
     * @param realmName Name of Realm.
     * @param viewbeanClassName Class name of View Bean
     * @throws AMConsoleException if there are no attributes to display.
     * @return realm profile property XML.
     */
    public String getRealmProfilePropertyXML(
            String realmName,
            String viewbeanClassName
    ) throws AMConsoleException {
        DelegationConfig dConfig = DelegationConfig.getInstance();
        boolean canModify = dConfig.hasPermission(realmName, null,
                AMAdminConstants.PERMISSION_MODIFY, this, viewbeanClassName);
        StringBuffer buff = new StringBuffer(2000);
        buff.append(PropertyXMLBuilderBase.getXMLDefinitionHeader())
                .append(PropertyTemplate.START_TAG);
        getPropertyXML(buff, !canModify);
        buff.append(PropertyTemplate.END_TAG);
        return buff.toString();
    }

    private void getPropertyXML(StringBuffer buff, boolean readonly)
            throws AMConsoleException
    {
        try {
            OrganizationConfigManager orgMgr = new OrganizationConfigManager(
                    adminSSOToken, "/");
            Set serviceSchemas =  orgMgr.getServiceSchemas();

            for (Iterator iter = serviceSchemas.iterator(); iter.hasNext(); ) {
                MultiServicesPropertyXMLBuilder xmlBuilder =
                        new MultiServicesPropertyXMLBuilder(
                                (ServiceSchema)iter.next(), this);
                xmlBuilder.setAllAttributeReadOnly(readonly);
                buff.append(xmlBuilder.getXML(false));
            }
        } catch (SSOException e) {
            debug.error("RMRealmModelImpl.getPropertyXML", e);
        } catch (SMSException e) {
            debug.error("RMRealmModelImpl.getPropertyXML", e);
        }
    }

    /**
     * Initializes sessions list.
     *
     * @param pattern user id pattern to search for.
     * @throws AMConsoleException if unable to initialized the session list.
     */
    private void initSessionsList(String pattern) 
        throws AMConsoleException
    {
        pattern = pattern.toLowerCase();
        String[] params = {serverName, pattern};
        logEvent("ATTEMPT_GET_CURRENT_SESSIONS", params);

        try {
            Session session = Session.getSession(
                new SessionID(getUserSSOToken().getTokenID().toString()));
            SearchResults result = session.getValidSessions(
                serverName, pattern);
            Map<String, Session> sessions = (Map<String, Session>) result.getResultAttributes();
            String errorMessage =
                AMAdminUtils.getSearchResultWarningMessage(result, this);
            sessionCache = new SMSessionCache(
                sessions.values(), errorMessage, this);
            logEvent("SUCCEED_GET_CURRENT_SESSIONS", params);
        } catch (SessionException se) {
            String strError = getErrorString(se);
            String[] paramsEx = {serverName, pattern, strError};
            logEvent("SESSION_EXCEPTION_GET_CURRENT_SESSIONS", paramsEx);
            throw new AMConsoleException(strError);
        }
    }

    private Session getCurrentSession()
        throws AMConsoleException
    {
        Session session = null;

        try {
            session = Session.getSession(
                new SessionID(getUserSSOToken().getTokenID().toString()));
        } catch (SessionException se) {
            throw new AMConsoleException(getErrorString(se));
        }

        return session;
    }

    private Map getValidSessions(Session session, String pattern)
        throws AMConsoleException
    {
        Map sessions = Collections.EMPTY_MAP;

        try {
            SearchResults result = session.getValidSessions(
                serverName, pattern);
            Map<String, Session> validSessions = (Map<String, Session>) result.getResultAttributes();

            if ((validSessions != null) && !validSessions.isEmpty()) {
                sessions = new HashMap(validSessions.size());

                for (Iterator iter = validSessions.values().iterator();
                    iter.hasNext();
                ) {
                    Session s = (Session)iter.next();
                    if (s != null) {
                        sessions.put(s.getID().toString(), s);
                    }
                }
            }
        } catch (SessionException se) {
            throw new AMConsoleException(getErrorString(se));
        }

        return sessions;
    }

    /**
     * Invalidates list of session.
     *
     * @param list of session <code>ID</code>s.
     * @param pattern Search pattern.
     * @return a list of session <code>ID</code>s that cannot be validated.
     */
    public List invalidateSessions(List list, String pattern) 
        throws AMConsoleException
    {
        List failList = Collections.EMPTY_LIST;

        if ((list != null) && !list.isEmpty()) {
            Session session = getCurrentSession();
            Map validSessions = getValidSessions(session, pattern);
            list.retainAll(validSessions.keySet());

            if (!list.isEmpty()) {
                String currentSessionHandler = null;

                try {
                    currentSessionHandler =
                        session.getProperty(Session.SESSION_HANDLE_PROP);
                } catch (SessionException se) {
                    throw new AMConsoleException(getErrorString(se));
                }

                String[] params = new String[2];
                params[0] = serverName;
                String curSessionId = null;

                failList = new ArrayList(list.size());

                for (Iterator iter = list.iterator(); iter.hasNext(); ) {
                    String sessionId = (String)iter.next();
                    Session s = (Session)validSessions.get(sessionId);
                    params[1] = sessionId;
                    boolean isCurrentSession = false;

                     try {
                        isCurrentSession = currentSessionHandler.equals(
                            s.getProperty(Session.SESSION_HANDLE_PROP)); 
                    } catch (SessionException se) {
                        logEvent("SESSION_EXCEPTION_INVALIDATE_SESSIONS",
                            params);
                        throw new AMConsoleException(getErrorString(se));
                    }

                    if (isCurrentSession) {
                        curSessionId = sessionId;
                        validSession = false;
                    } else {
                        try {
                            logEvent("ATTEMPT_INVALIDATE_SESSIONS", params);
                            session.destroySession(s);
                            logEvent("SUCCEED_INVALIDATE_SESSIONS",params);
                        } catch (SessionException se) {
                            String[] paramsEx = {serverName, sessionId,
                                getErrorString(se)};
                            logEvent("SESSION_EXCEPTION_INVALIDATE_SESSIONS",
                                paramsEx);

                            try {
                                failList.add(s.getProperty(USER_ID));
                            } catch (SessionException e) {
                                debug.error(
                                    "SMProfileModelImpl.invalidateSessions", e);
                            }

                            debug.error(
                                "SMProfileModelImpl.invalidateSessions", se);
                        }
                    }
                }
            
                if (!validSession) {
                    params[1] = curSessionId;
                    logEvent("ATTEMPT_INVALIDATE_SESSIONS", params);

                    try {
                        session.destroySession(session);
                        logEvent("SUCCEED_INVALIDATE_SESSIONS",params);
                    } catch (SessionException se) {
                        String[] paramsEx = {serverName, curSessionId,
                            getErrorString(se)};
                        logEvent("SESSION_EXCEPTION_INVALIDATE_SESSIONS",
                            paramsEx);
                    }
                }
            }
        }

        return failList;
    }

    /**
     * Returns true if current user session state is valid.
     *
     * @return true is the session is valid.
     */
    public boolean isSessionValid() {
        return validSession;
    }


    /**
     * Returns true if the two sessions passed are same.
     *
     * @param session current session
     * @param sess session to be destroyed.
     * @return true if two sessions are same
     */
    private boolean isSessionsEquals(Session session, Session sess) {
        boolean isCurrentSession = false;
        try {
            String currentSessionHandler =
                session.getProperty(Session.SESSION_HANDLE_PROP);
            isCurrentSession = currentSessionHandler.equals(
                sess.getProperty(Session.SESSION_HANDLE_PROP));
        } catch (SessionException se) {
            debug.error ("Could not determined if the sessions are same ", se);
        }
        return isCurrentSession;
    }

    /**
     * Returns session cache.
     *
     * @param pattern Pattern for search.
     * @return session cache.
     * @throws AMConsoleException if unable to get the session cache.
     */
    public SMSessionCache getSessionCache(String pattern) 
        throws AMConsoleException
    {
        if (sessionCache == null) {
            initSessionsList(pattern);
        }

        return sessionCache;
    }

    /**
     * Sets session cache.
     *
     * @param cache Session cache.
     */
    public void setSessionCache(SMSessionCache cache) {
        sessionCache = cache;
    }

    /**
     * Returns set of server names.
     *
     * @return set of server names.
     */
    public Map getServerNames() {
        if (serverNamesMap == null || serverNamesMap.isEmpty()) {
            try {
                Set names = ServerConfiguration.getServerInfo(
                    getUserSSOToken());
                names = parseServerNames(names);
                serverNamesMap = getMapValues(names);
            } catch (SSOException e) {
                debug.error("SMProfileModelImpl.getServerNames", e);
            } catch (SMSException e) {
                debug.error("SMProfileModelImpl.getServerNames", e);                
            }
        }
        return serverNamesMap;
    }


    private Set parseServerNames(Set servers) {
        Set serverList = new HashSet();
        Iterator iter = servers.iterator();
        while(iter.hasNext()) {
            String serverEntry = (String)iter.next();
            int index = serverEntry.indexOf(DELIMITER);
            if (index != -1) {
                String server = serverEntry.substring(0, index);
                serverList.add(server);
            } else {
                if (debug.warningEnabled()) {
                    debug.warning("SMProfileModelImpl.parseServerNames:" +
                        "This server entry is not proper, ignoring:"
                        + serverEntry);
                }
            }
        }
        return serverList;
    }

    /**
     * Returns the map of server names(host:port) and url.
     *
     * @param set names of server instance.
     * returns the map of server names(host:port) and url. 
     */
    private Map getMapValues(Set set) {
        Map map = Collections.EMPTY_MAP;
        if (set != null && !set.isEmpty()) {
            Iterator iter = set.iterator();
            map = new HashMap(set.size()*2);
            while (iter.hasNext()) {
                String url = (String)iter.next();
                try {
                     URL u = new URL(url);
                     String host = u.getHost();
                     int port = u.getPort();
                     if (port == -1) {
                         map.put(host, url);
                     } else {
                         String server = host + ":" + port;
                         map.put(server, url);
                     }
                } catch (MalformedURLException e) {
                    debug.error("SMProfileModelImpl.getMapValues", e);
                }
            }
        }
        return map;
    }

    /**
     * Returns attribute values. Map of attribute name to set of values.
     *
     * @param name Name of Realm.
     * @return attribute values.
     * @throws AMConsoleException if attribute values cannot be obtained.
     */
    public Map getAttributeValues(String name)
            throws AMConsoleException
    {
        String[] param = {name};
        logEvent("ATTEMPT_GET_ATTR_VALUES_OF_REALM", param);

        try {
            Map map = new HashMap();
            OrganizationConfigManager orgMgr = new OrganizationConfigManager(
                    getUserSSOToken(), name);
            Set serviceSchemas = orgMgr.getServiceSchemas();

            for (Iterator iter = serviceSchemas.iterator(); iter.hasNext();) {
                ServiceSchema ss = (ServiceSchema)iter.next();
                String serviceName = ss.getServiceName();
                Map values = orgMgr.getAttributes(serviceName);
                Set attributeSchemas = ss.getAttributeSchemas();

                for (Iterator i = attributeSchemas.iterator(); i.hasNext(); ) {
                    AttributeSchema as = (AttributeSchema)i.next();
                    String i18nKey = as.getI18NKey();

                    if ((i18nKey != null) && (i18nKey.trim().length() > 0)) {
                        String attrName = as.getName();
                        Set val = (Set)values.get(attrName);
                        if (val == null) {
                            val = Collections.EMPTY_SET;
                        }
                        map.put(serviceName + "_" + attrName, val);
                    }
                }
            }
            logEvent("SUCCEED_GET_ATTR_VALUES_OF_REALM", param);

            return map;
        } catch (SMSException e) {
            String strError = getErrorString(e);
            String[] paramsEx = {name, strError};
            logEvent("SMS_EXCEPTION_GET_ATTR_VALUES_OF_REALM", paramsEx);
            throw new AMConsoleException(strError);
        }
    }

    /**
     * Set attribute values.
     *
     * @param name Name of Realm.
     * @param attributeValues Map of attribute name to set of values.
     * @throws AMConsoleException if attribute values cannot be updated.
     */
    public void setAttributeValues(String name, Map attributeValues)
            throws AMConsoleException {
        try {
            String[] param = {name};
            logEvent("ATTEMPT_SET_ATTR_VALUES_OF_REALM", param);
            OrganizationConfigManager orgMgr = new OrganizationConfigManager(
                    getUserSSOToken(), name);
            Map map = mapAttributeValuesToServiceName(attributeValues);

            for (Iterator iter = map.keySet().iterator(); iter.hasNext(); ) {
                String serviceName = (String)iter.next();
                orgMgr.setAttributes(serviceName, (Map)map.get(serviceName));
            }
            logEvent("SUCCEED_SET_ATTR_VALUES_OF_REALM", param);
        } catch (SMSException e) {
            String strError = getErrorString(e);
            String[] paramsEx = {name, strError};
            logEvent("SMS_EXCEPTION_SET_ATTR_VALUES_OF_REALM", paramsEx);
            throw new AMConsoleException(strError);
        }
    }

    private Map mapAttributeValuesToServiceName(Map attributeValues) {
        Map mapServices = null;

        if ((attributeValues != null) && !attributeValues.isEmpty()) {
            mapServices = new HashMap();

            for (Iterator i = attributeValues.keySet().iterator();
                 i.hasNext();
                    ) {
                String attrName = (String)i.next();
                int idx = attrName.indexOf('_');
                if (idx != -1) {
                    String serviceName = attrName.substring(0, idx);
                    String attributeName = attrName.substring(idx+1);

                    Map map = (Map)mapServices.get(serviceName);
                    if (map == null) {
                        map = new HashMap();
                        mapServices.put(serviceName, map);
                    }
                    map.put(attributeName, attributeValues.get(attrName));
                } else {
                    debug.error(
                            "RMRealmModelImpl.mapAttributeValuesToServiceName: " +
                                    "unknown attribute, " + attrName);
                }
            }
        }

        return (mapServices == null) ? Collections.EMPTY_MAP : mapServices;
    }
}
