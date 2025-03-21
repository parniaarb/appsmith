package com.appsmith.server.repositories.ce;

import com.appsmith.server.domains.Plugin;
import com.appsmith.server.repositories.BaseAppsmithRepositoryImpl;
import com.appsmith.server.repositories.CacheableRepositoryHelper;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;

import static org.springframework.data.mongodb.core.query.Criteria.where;

public class CustomPluginRepositoryCEImpl extends BaseAppsmithRepositoryImpl<Plugin>
        implements CustomPluginRepositoryCE {

    public CustomPluginRepositoryCEImpl(
            ReactiveMongoOperations mongoOperations,
            MongoConverter mongoConverter,
            CacheableRepositoryHelper cacheableRepositoryHelper) {
        super(mongoOperations, mongoConverter, cacheableRepositoryHelper);
    }

    @Override
    public Flux<Plugin> findDefaultPluginIcons() {
        Criteria criteria = Criteria.where(Plugin.Fields.defaultInstall).is(Boolean.TRUE);
        List<String> projections = List.of(Plugin.Fields.name, Plugin.Fields.packageName, Plugin.Fields.iconLocation);
        return queryBuilder().criteria(criteria).fields(projections).all();
    }

    @Override
    public Flux<Plugin> findAllByIdsWithoutPermission(Set<String> ids, List<String> includeFields) {
        Criteria idCriteria = where(Plugin.Fields.id).in(ids);
        return queryBuilder().criteria(idCriteria).fields(includeFields).all();
    }
}
