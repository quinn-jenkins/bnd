package aQute.bnd.osgi.resource;

import static aQute.lib.collections.Logic.retain;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.repository.RepositoryContent;

import aQute.bnd.osgi.resource.ResourceUtils.ContentCapability;

class ResourceImpl implements Resource, Comparable<Resource>, RepositoryContent {

	private volatile List<Capability>				allCapabilities;
	private volatile Map<String, List<Capability>>	capabilityMap;
	private volatile List<Requirement>				allRequirements;
	private volatile Map<String, List<Requirement>>	requirementMap;

	private volatile transient Map<URI, String>		locations;

	void setCapabilities(List<Capability> capabilities) {
		allCapabilities = unmodifiableList(capabilities);
		capabilityMap = capabilities.stream()
			.collect(groupingBy(Capability::getNamespace, collectingAndThen(toList(), Collections::unmodifiableList)));

		locations = null; // clear so equals/hashCode can recompute
	}

	@Override
	public List<Capability> getCapabilities(String namespace) {
		List<Capability> caps = (namespace != null) ? ((capabilityMap != null) ? capabilityMap.get(namespace) : null)
			: allCapabilities;

		return (caps != null) ? caps : Collections.emptyList();
	}

	void setRequirements(List<Requirement> requirements) {
		allRequirements = unmodifiableList(requirements);
		requirementMap = requirements.stream()
			.collect(groupingBy(Requirement::getNamespace, collectingAndThen(toList(), Collections::unmodifiableList)));
	}

	@Override
	public List<Requirement> getRequirements(String namespace) {
		List<Requirement> reqs = (namespace != null) ? ((requirementMap != null) ? requirementMap.get(namespace) : null)
			: allRequirements;

		return (reqs != null) ? reqs : Collections.emptyList();
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		List<Capability> identities = getCapabilities(IdentityNamespace.IDENTITY_NAMESPACE);
		if (identities.size() == 1) {
			Capability idCap = identities.get(0);
			Object id = idCap.getAttributes()
				.get(IdentityNamespace.IDENTITY_NAMESPACE);
			builder.append(id);

			Object version = idCap.getAttributes()
				.get(IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE);
			if (version != null) {
				builder.append(" version=")
					.append(version);
			}
		} else {
			// Generic toString
			builder.append("ResourceImpl [caps=");
			builder.append(allCapabilities);
			builder.append(", reqs=");
			builder.append(allRequirements);
			builder.append(']');
		}
		return builder.toString();
	}

	@Override
	public int compareTo(Resource o) {
		return ResourceUtils.compareTo(this, o);
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;

		if (!(other instanceof Resource))
			return false;

		Map<URI, String> thisLocations = getLocations();
		Map<URI, String> otherLocations = (other instanceof ResourceImpl) ? ((ResourceImpl) other).getLocations()
			: ResourceUtils.getLocations((Resource) other);

		Collection<URI> overlap = retain(thisLocations.keySet(), otherLocations.keySet());

		return overlap.stream()
			.anyMatch(uri -> {
				String thisSha = thisLocations.get(uri);
				String otherSha = otherLocations.get(uri);
				return Objects.equals(thisSha, otherSha);
			});
	}

	private Map<URI, String> getLocations() {
		Map<URI, String> map = locations;
		if (map != null) {
			return map;
		}
		return locations = ResourceUtils.getLocations(this);
	}

	@Override
	public int hashCode() {
		return getLocations().hashCode();
	}

	@Override
	public InputStream getContent() {
		try {
			ContentCapability c = ResourceUtils.getContentCapability(this);
			URI url = c.url();
			return url.toURL()
				.openStream();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
