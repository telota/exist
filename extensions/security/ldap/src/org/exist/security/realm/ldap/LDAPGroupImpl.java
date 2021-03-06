package org.exist.security.realm.ldap;

import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.security.AbstractGroup;
import org.exist.security.AbstractRealm;
import org.exist.storage.DBBroker;

/**
 *
 * @author aretter
 */
@ConfigurationClass("group")
public class LDAPGroupImpl extends AbstractGroup {

    public LDAPGroupImpl(AbstractRealm realm, Configuration configuration) throws ConfigurationException {
        super(realm, configuration);
    }

    public LDAPGroupImpl(DBBroker broker, AbstractRealm realm, int id, String name) throws ConfigurationException {
        super(broker, realm, id, name, null);
    }

    LDAPGroupImpl(AbstractRealm realm, Configuration config, boolean removed) throws ConfigurationException {
        this(realm, config);
        this.removed = removed;
    }

    LDAPGroupImpl(DBBroker broker, AbstractRealm realm, String name) throws ConfigurationException {
        super(broker, realm, name);
    }
}