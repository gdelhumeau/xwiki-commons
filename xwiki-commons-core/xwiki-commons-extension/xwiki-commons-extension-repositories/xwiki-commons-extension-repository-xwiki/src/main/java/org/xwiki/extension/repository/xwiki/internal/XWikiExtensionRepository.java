/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.extension.repository.xwiki.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.AuthCache;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.CloseableHttpClient;
import org.xwiki.extension.Extension;
import org.xwiki.extension.ExtensionDependency;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.ExtensionLicenseManager;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.repository.AbstractExtensionRepository;
import org.xwiki.extension.repository.DefaultExtensionRepositoryDescriptor;
import org.xwiki.extension.repository.ExtensionRepositoryDescriptor;
import org.xwiki.extension.repository.http.internal.HttpClientFactory;
import org.xwiki.extension.repository.result.CollectionIterableResult;
import org.xwiki.extension.repository.result.IterableResult;
import org.xwiki.extension.repository.search.SearchException;
import org.xwiki.extension.repository.search.Searchable;
import org.xwiki.extension.repository.xwiki.model.jaxb.ExtensionVersion;
import org.xwiki.extension.repository.xwiki.model.jaxb.ExtensionVersionSummary;
import org.xwiki.extension.repository.xwiki.model.jaxb.ExtensionVersions;
import org.xwiki.extension.repository.xwiki.model.jaxb.ExtensionsSearchResult;
import org.xwiki.extension.version.Version;
import org.xwiki.extension.version.VersionConstraint;
import org.xwiki.extension.version.internal.DefaultVersion;
import org.xwiki.repository.Resources;
import org.xwiki.repository.UriBuilder;

/**
 * @version $Id$
 * @since 4.0M1
 */
public class XWikiExtensionRepository extends AbstractExtensionRepository implements Searchable
{
    private final transient XWikiExtensionRepositoryFactory repositoryFactory;

    private final transient ExtensionLicenseManager licenseManager;

    private final transient HttpClientFactory httpClientFactory;

    private final transient UriBuilder extensionVersionUriBuider;

    private final transient UriBuilder extensionVersionFileUriBuider;

    private final transient UriBuilder extensionVersionsUriBuider;

    private final transient UriBuilder searchUriBuider;

    private HttpClientContext localContext;

    public XWikiExtensionRepository(ExtensionRepositoryDescriptor repositoryDescriptor,
        XWikiExtensionRepositoryFactory repositoryFactory, ExtensionLicenseManager licenseManager,
        HttpClientFactory httpClientFactory) throws Exception
    {
        super(repositoryDescriptor.getURI().getPath().endsWith("/") ? new DefaultExtensionRepositoryDescriptor(
            repositoryDescriptor.getId(), repositoryDescriptor.getType(), new URI(StringUtils.chop(repositoryDescriptor
                .getURI().toString()))) : repositoryDescriptor);

        this.repositoryFactory = repositoryFactory;
        this.licenseManager = licenseManager;
        this.httpClientFactory = httpClientFactory;

        // Uri builders
        this.extensionVersionUriBuider = createUriBuilder(Resources.EXTENSION_VERSION);
        this.extensionVersionFileUriBuider = createUriBuilder(Resources.EXTENSION_VERSION_FILE);
        this.extensionVersionsUriBuider = createUriBuilder(Resources.EXTENSION_VERSIONS);
        this.searchUriBuider = createUriBuilder(Resources.SEARCH);

        // Setup preemptive authentication
        if (getDescriptor().getProperty("auth.user") != null) {
            // Create AuthCache instance
            AuthCache authCache = new BasicAuthCache();
            // Generate BASIC scheme object and add it to the local
            // auth cache
            BasicScheme basicAuth = new BasicScheme();
            authCache.put(new HttpHost(getDescriptor().getURI().getHost(), getDescriptor().getURI().getPort(),
                getDescriptor().getURI().getScheme()), basicAuth);

            // Add AuthCache to the execution context
            this.localContext = HttpClientContext.create();
            this.localContext.setAuthCache(authCache);
        }
    }

    protected UriBuilder getExtensionFileUriBuider()
    {
        return this.extensionVersionFileUriBuider;
    }

    protected CloseableHttpResponse getRESTResource(UriBuilder builder, Object... values) throws IOException
    {
        String url;
        try {
            url = builder.build(values).toString();
        } catch (Exception e) {
            throw new IOException("Failed to build REST URL", e);
        }

        CloseableHttpClient httpClient = this.httpClientFactory.createClient(
            getDescriptor().getProperty("auth.user"), getDescriptor().getProperty("auth.password"));

        HttpGet getMethod = new HttpGet(url);
        getMethod.addHeader("Accept", "application/xml");
        CloseableHttpResponse response;
        try {
            if (this.localContext != null) {
                response = httpClient.execute(getMethod, this.localContext);
            } else {
                response = httpClient.execute(getMethod);
            }
        } catch (Exception e) {
            throw new IOException(String.format("Failed to request [%s]", getMethod.getURI()), e);
        }

        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new IOException(String.format("Invalid answer [%s] from the server when requesting [%s]",
                response.getStatusLine().getStatusCode(), getMethod.getURI()));
        }

        return response;
    }

    protected InputStream getRESTResourceAsStream(UriBuilder builder, Object... values) throws IOException
    {
        return getRESTResource(builder, values).getEntity().getContent();
    }

    private UriBuilder createUriBuilder(String path)
    {
        return new UriBuilder(getDescriptor().getURI(), path);
    }

    // ExtensionRepository

    @Override
    public Extension resolve(ExtensionId extensionId) throws ResolveException
    {
        try {
            return new XWikiExtension(this, (ExtensionVersion) this.repositoryFactory.getUnmarshaller().unmarshal(
                getRESTResourceAsStream(this.extensionVersionUriBuider, extensionId.getId(), extensionId.getVersion()
                    .getValue())), this.licenseManager);
        } catch (Exception e) {
            throw new ResolveException("Failed to create extension object for extension [" + extensionId + "]", e);
        }
    }

    @Override
    public Extension resolve(ExtensionDependency extensionDependency) throws ResolveException
    {
        VersionConstraint constraint = extensionDependency.getVersionConstraint();

        try {
            Version version;
            if (!constraint.getRanges().isEmpty()) {
                ExtensionVersions versions =
                    resolveExtensionVersions(extensionDependency.getId(), constraint, 0, -1, false);
                if (versions.getExtensionVersionSummaries().isEmpty()) {
                    throw new ResolveException("Can't find any version with id [" + extensionDependency.getId()
                        + "] matching version constraint [" + extensionDependency.getVersionConstraint() + "]");
                }

                version =
                    new DefaultVersion(versions.getExtensionVersionSummaries()
                        .get(versions.getExtensionVersionSummaries().size() - 1).getVersion());
            } else {
                version = constraint.getVersion();
            }

            return new XWikiExtension(this, (ExtensionVersion) this.repositoryFactory.getUnmarshaller().unmarshal(
                getRESTResourceAsStream(this.extensionVersionUriBuider, extensionDependency.getId(), version)),
                this.licenseManager);
        } catch (Exception e) {
            throw new ResolveException("Failed to create extension object for extension dependency ["
                + extensionDependency + "]", e);
        }
    }

    private ExtensionVersions resolveExtensionVersions(String id, VersionConstraint constraint, int offset, int nb,
        boolean requireTotalHits) throws ResolveException
    {
        UriBuilder builder = this.extensionVersionsUriBuider.clone();

        builder.queryParam(Resources.QPARAM_LIST_REQUIRETOTALHITS, requireTotalHits);
        builder.queryParam(Resources.QPARAM_LIST_START, offset);
        builder.queryParam(Resources.QPARAM_LIST_NUMBER, nb);
        if (constraint != null) {
            builder.queryParam(Resources.QPARAM_VERSIONS_RANGES, constraint.getValue());
        }

        try {
            return (ExtensionVersions) this.repositoryFactory.getUnmarshaller().unmarshal(
                getRESTResourceAsStream(builder, id));
        } catch (Exception e) {
            throw new ResolveException("Failed to find version for extension id [" + id + "]", e);
        }
    }

    @Override
    public IterableResult<Version> resolveVersions(String id, int offset, int nb) throws ResolveException
    {
        ExtensionVersions restExtensions = resolveExtensionVersions(id, null, offset, nb, true);

        List<Version> versions = new ArrayList<Version>(restExtensions.getExtensionVersionSummaries().size());
        for (ExtensionVersionSummary restExtension : restExtensions.getExtensionVersionSummaries()) {
            versions.add(new DefaultVersion(restExtension.getVersion()));
        }

        return new CollectionIterableResult<Version>(restExtensions.getTotalHits(), restExtensions.getOffset(),
            versions);
    }

    // Searchable

    @Override
    public IterableResult<Extension> search(String pattern, int offset, int nb) throws SearchException
    {
        UriBuilder builder = this.searchUriBuider.clone();

        builder.queryParam(Resources.QPARAM_LIST_START, offset);
        builder.queryParam(Resources.QPARAM_LIST_NUMBER, nb);
        builder.queryParam(Resources.QPARAM_SEARCH_QUERY, pattern);

        ExtensionsSearchResult restExtensions;
        try {
            restExtensions =
                (ExtensionsSearchResult) this.repositoryFactory.getUnmarshaller().unmarshal(
                    getRESTResourceAsStream(builder));
        } catch (Exception e) {
            throw new SearchException("Failed to search extensions based on pattern [" + pattern + "]", e);
        }

        List<Extension> extensions = new ArrayList<Extension>(restExtensions.getExtensions().size());
        for (ExtensionVersion restExtension : restExtensions.getExtensions()) {
            extensions.add(new XWikiExtension(this, restExtension, this.licenseManager));
        }

        return new CollectionIterableResult<Extension>(restExtensions.getTotalHits(), restExtensions.getOffset(),
            extensions);
    }
}
