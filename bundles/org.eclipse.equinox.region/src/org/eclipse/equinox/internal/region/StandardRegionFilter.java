/*******************************************************************************
 * Copyright (c) 2011, 2016 VMware Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   VMware Inc. - initial contribution
 *   IBM - bug fixes and enhancements
 *******************************************************************************/

package org.eclipse.equinox.internal.region;

import java.util.*;
import org.eclipse.equinox.region.RegionFilter;
import org.osgi.framework.*;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;

public final class StandardRegionFilter implements RegionFilter {
	final static Filter ALL;

	static {
		try {
			ALL = FrameworkUtil.createFilter("(|(!(all=*))(all=*))"); //$NON-NLS-1$
		} catch (InvalidSyntaxException e) {
			// should never happen!
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private static final String BUNDLE_ID_ATTR = "id"; //$NON-NLS-1$
	private final Map<String, Collection<Filter>> filters;

	public StandardRegionFilter(Map<String, Collection<Filter>> filters) {
		if (filters == null) {
			throw new IllegalArgumentException("filters must not be null."); //$NON-NLS-1$
		}
		// must perform deep copy to avoid external changes
		this.filters = new HashMap<String, Collection<Filter>>((int) ((filters.size() / 0.75) + 1));
		for (Map.Entry<String, Collection<Filter>> namespace : filters.entrySet()) {
			Collection<Filter> namespaceFilters = new ArrayList<Filter>(namespace.getValue());
			this.filters.put(namespace.getKey(), namespaceFilters);
		}
	}

	public boolean isAllowed(Bundle bundle) {
		if (bundle == null) {
			return false;
		}
		HashMap<String, Object> attrs = new HashMap<String, Object>(4);
		String bsn = bundle.getSymbolicName();
		if (bsn != null) {
			attrs.put(VISIBLE_BUNDLE_NAMESPACE, bsn);
			attrs.put(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE, bsn);
		}
		attrs.put(org.osgi.framework.Constants.BUNDLE_VERSION_ATTRIBUTE, bundle.getVersion());
		attrs.put(BUNDLE_ID_ATTR, bundle.getBundleId());
		return isBundleAllowed(attrs);
	}

	public boolean isAllowed(BundleRevision bundle) {
		HashMap<String, Object> attrs = new HashMap<String, Object>(4);
		String bsn = bundle.getSymbolicName();
		if (bsn != null) {
			attrs.put(VISIBLE_BUNDLE_NAMESPACE, bsn);
			attrs.put(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE, bsn);
		}
		attrs.put(org.osgi.framework.Constants.BUNDLE_VERSION_ATTRIBUTE, bundle.getVersion());
		attrs.put(BUNDLE_ID_ATTR, getBundleId(bundle));
		return isBundleAllowed(attrs);
	}

	/**
	 * Determines whether this filter allows the bundle with the given attributes
	 * 
	 * @param bundleAttributes the bundle attributes
	 * @return <code>true</code> if the bundle is allowed and <code>false</code>otherwise
	 */
	private boolean isBundleAllowed(Map<String, ?> bundleAttributes) {
		return isAllowed(VISIBLE_BUNDLE_NAMESPACE, bundleAttributes);
	}

	private static boolean match(Collection<Filter> filters, Map<String, ?> attrs) {
		if (filters == null)
			return false;
		for (Filter filter : filters) {
			if (filter == ALL || filter.matches(attrs))
				return true;
		}
		return false;
	}

	private static boolean match(Collection<Filter> filters, ServiceReference<?> service) {
		if (filters == null)
			return false;
		for (Filter filter : filters) {
			if (filter == ALL || filter.match(service))
				return true;
		}
		return false;
	}

	public boolean isAllowed(ServiceReference<?> service) {
		if (match(filters.get(VISIBLE_OSGI_SERVICE_NAMESPACE), service))
			return true;
		return matchAll(VISIBLE_OSGI_SERVICE_NAMESPACE, service);
	}

	public boolean isAllowed(BundleCapability capability) {
		return isAllowed(capability.getNamespace(), capability.getAttributes());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isAllowed(String namespace, Map<String, ?> attributes) {
		if (match(filters.get(namespace), attributes))
			return true;
		return matchAll(namespace, attributes);
	}

	@SuppressWarnings("deprecation")
	static final String[] serviceNamespaces = new String[] {VISIBLE_OSGI_SERVICE_NAMESPACE, VISIBLE_SERVICE_NAMESPACE};

	private boolean matchAll(final String namespace, final Map<String, ?> attributes) {
		Collection<Filter> allMatching = filters.get(VISIBLE_ALL_NAMESPACE);
		if (allMatching == null) {
			return false;
		}
		return match(allMatching, new AbstractMap<String, Object>() {
			@SuppressWarnings("deprecation")
			@Override
			public Object get(Object key) {
				if (RegionFilter.VISIBLE_ALL_NAMESPACE_ATTRIBUTE.equals(key)) {
					if (VISIBLE_SERVICE_NAMESPACE.equals(namespace) || VISIBLE_OSGI_SERVICE_NAMESPACE.equals(namespace)) {
						return serviceNamespaces;
					}
					return namespace;
				}
				return attributes.get(key);
			}

			@Override
			public Set<java.util.Map.Entry<String, Object>> entrySet() {
				throw new UnsupportedOperationException();
			}
		});
	}

	private boolean matchAll(final String namespace, final ServiceReference<?> service) {
		Collection<Filter> allMatching = filters.get(VISIBLE_ALL_NAMESPACE);
		if (allMatching == null) {
			return false;
		}
		return match(allMatching, new AbstractMap<String, Object>() {
			@SuppressWarnings("deprecation")
			@Override
			public Object get(Object key) {
				if (RegionFilter.VISIBLE_ALL_NAMESPACE_ATTRIBUTE.equals(key)) {
					if (VISIBLE_SERVICE_NAMESPACE.equals(namespace) || VISIBLE_OSGI_SERVICE_NAMESPACE.equals(namespace)) {
						return serviceNamespaces;
					}
					return namespace;
				}
				return service.getProperty((String) key);
			}

			@Override
			public Set<java.util.Map.Entry<String, Object>> entrySet() {
				throw new UnsupportedOperationException();
			}
		});
	}

	public Map<String, Collection<String>> getSharingPolicy() {
		Map<String, Collection<String>> result = new HashMap<String, Collection<String>>((int) ((filters.size() / 0.75) + 1));
		for (Map.Entry<String, Collection<Filter>> namespace : filters.entrySet()) {
			result.put(namespace.getKey(), getFilters(namespace.getValue()));
		}
		return result;
	}

	private static Collection<String> getFilters(Collection<Filter> filters) {
		Collection<String> result = new ArrayList<String>(filters.size());
		for (Filter filter : filters) {
			result.add(filter.toString());
		}
		return result;
	}

	public String toString() {
		return getSharingPolicy().toString();
	}

	private Long getBundleId(BundleRevision bundleRevision) {
		return EquinoxStateHelper.getBundleId(bundleRevision);
	}
}
