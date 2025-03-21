package com.appsmith.server.repositories.ce;

import com.appsmith.external.models.BaseDomain;
import com.appsmith.server.acl.AclPermission;
import com.appsmith.server.domains.Application;
import com.appsmith.server.domains.ApplicationPage;
import com.appsmith.server.domains.User;
import com.appsmith.server.repositories.BaseAppsmithRepositoryImpl;
import com.appsmith.server.repositories.CacheableRepositoryHelper;
import com.appsmith.server.solutions.ApplicationPermission;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoOperations;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.appsmith.server.helpers.ce.bridge.Bridge.bridge;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Slf4j
public class CustomApplicationRepositoryCEImpl extends BaseAppsmithRepositoryImpl<Application>
        implements CustomApplicationRepositoryCE {

    private final CacheableRepositoryHelper cacheableRepositoryHelper;
    private final ApplicationPermission applicationPermission;

    @Autowired
    public CustomApplicationRepositoryCEImpl(
            @NonNull ReactiveMongoOperations mongoOperations,
            @NonNull MongoConverter mongoConverter,
            CacheableRepositoryHelper cacheableRepositoryHelper,
            ApplicationPermission applicationPermission) {
        super(mongoOperations, mongoConverter, cacheableRepositoryHelper);
        this.cacheableRepositoryHelper = cacheableRepositoryHelper;
        this.applicationPermission = applicationPermission;
    }

    @Override
    protected Criteria getIdCriteria(Object id) {
        return where(Application.Fields.id).is(id);
    }

    @Override
    public Mono<Application> findByIdAndWorkspaceId(String id, String workspaceId, AclPermission permission) {
        return queryBuilder()
                .byId(id)
                .criteria(bridge().equal(Application.Fields.workspaceId, workspaceId))
                .permission(permission)
                .one();
    }

    @Override
    public Mono<Application> findByName(String name, AclPermission permission) {
        return queryBuilder()
                .criteria(bridge().equal(Application.Fields.name, name))
                .permission(permission)
                .one();
    }

    @Override
    public Flux<Application> findByWorkspaceId(String workspaceId, AclPermission permission) {
        return queryBuilder()
                .criteria(bridge().equal(Application.Fields.workspaceId, workspaceId))
                .permission(permission)
                .all();
    }

    @Override
    public Flux<Application> findByMultipleWorkspaceIds(Set<String> workspaceIds, AclPermission permission) {
        Criteria workspaceIdCriteria = where(Application.Fields.workspaceId).in(workspaceIds);
        return queryBuilder()
                .criteria(workspaceIdCriteria)
                .permission(permission)
                .all();
    }

    @Override
    public Flux<Application> findAllUserApps(AclPermission permission) {
        Mono<User> currentUserWithTenantMono = ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .map(auth -> (User) auth.getPrincipal())
                .flatMap(user -> {
                    if (user.getTenantId() == null) {
                        return cacheableRepositoryHelper.getDefaultTenantId().map(tenantId -> {
                            user.setTenantId(tenantId);
                            return user;
                        });
                    }
                    return Mono.just(user);
                });

        return currentUserWithTenantMono
                .flatMap(cacheableRepositoryHelper::getPermissionGroupsOfUser)
                .flatMapMany(permissionGroups -> queryBuilder()
                        .permission(permission)
                        .permissionGroups(permissionGroups)
                        .all());
    }

    @Override
    public Flux<Application> findByClonedFromApplicationId(String applicationId, AclPermission permission) {
        return queryBuilder()
                .criteria(bridge().equal(Application.Fields.clonedFromApplicationId, applicationId))
                .permission(permission)
                .all();
    }

    @Override
    public Mono<Integer> addPageToApplication(
            String applicationId, String pageId, boolean isDefault, String defaultPageId) {
        final ApplicationPage applicationPage = new ApplicationPage();
        applicationPage.setIsDefault(isDefault);
        applicationPage.setDefaultPageId(defaultPageId);
        applicationPage.setId(pageId);
        return queryBuilder()
                .byId(applicationId)
                .updateFirst(new Update().push(Application.Fields.pages, applicationPage));
    }

    @Override
    public Mono<Integer> setPages(String applicationId, List<ApplicationPage> pages) {
        return queryBuilder().byId(applicationId).updateFirst(new Update().set(Application.Fields.pages, pages));
    }

    @Override
    public Mono<Void> setDefaultPage(String applicationId, String pageId) {
        // Since this can only happen during edit, the page in question is unpublished page. Hence the update should
        // be to pages and not publishedPages

        final Mono<Integer> setAllAsNonDefaultMono = queryBuilder()
                .byId(applicationId)
                .criteria(bridge().isTrue("pages.isDefault"))
                .updateFirst(new Update().set("pages.$.isDefault", false));

        final Mono<Integer> setDefaultMono = queryBuilder()
                .byId(applicationId)
                .criteria(bridge().equal("pages._id", new ObjectId(pageId)))
                .updateFirst(new Update().set("pages.$.isDefault", true));

        return setAllAsNonDefaultMono.then(setDefaultMono).then();
    }

    @Override
    @Deprecated
    public Mono<Application> getApplicationByGitBranchAndDefaultApplicationId(
            String defaultApplicationId, String branchName, AclPermission aclPermission) {
        return getApplicationByGitBranchAndDefaultApplicationId(defaultApplicationId, null, branchName, aclPermission);
    }

    @Override
    public Mono<Application> getApplicationByGitBranchAndDefaultApplicationId(
            String defaultApplicationId,
            List<String> projectionFieldNames,
            String branchName,
            AclPermission aclPermission) {

        return queryBuilder()
                .criteria(bridge().equal(
                                Application.Fields.gitApplicationMetadata_defaultApplicationId, defaultApplicationId)
                        .equal(Application.Fields.gitApplicationMetadata_branchName, branchName))
                .fields(projectionFieldNames)
                .permission(aclPermission)
                .one();
    }

    @Override
    public Mono<Application> getApplicationByGitBranchAndDefaultApplicationId(
            String defaultApplicationId, String branchName, Optional<AclPermission> aclPermission) {

        return queryBuilder()
                .criteria(bridge().equal(
                                Application.Fields.gitApplicationMetadata_defaultApplicationId, defaultApplicationId)
                        .equal(Application.Fields.gitApplicationMetadata_branchName, branchName))
                .permission(aclPermission.orElse(null))
                .one();
    }

    @Override
    public Flux<Application> getApplicationByGitDefaultApplicationId(
            String defaultApplicationId, AclPermission permission) {

        return queryBuilder()
                .criteria(bridge().equal(
                                Application.Fields.gitApplicationMetadata_defaultApplicationId, defaultApplicationId))
                .permission(permission)
                .all();
    }

    @Override
    public Mono<Long> countByWorkspaceId(String workspaceId) {
        return queryBuilder()
                .criteria(bridge().equal(Application.Fields.workspaceId, workspaceId))
                .count();
    }

    @Override
    public Mono<Long> getGitConnectedApplicationWithPrivateRepoCount(String workspaceId) {
        return queryBuilder()
                .criteria(bridge().equal(Application.Fields.workspaceId, workspaceId)
                        .isTrue(Application.Fields.gitApplicationMetadata_isRepoPrivate))
                .count();
    }

    @Override
    public Flux<Application> getGitConnectedApplicationByWorkspaceId(String workspaceId) {
        AclPermission aclPermission = applicationPermission.getEditPermission();
        return queryBuilder()
                .criteria(bridge()
                        // isRepoPrivate and gitAuth will be stored only with default application which ensures we will
                        // have only single application per repo
                        .exists(Application.Fields.gitApplicationMetadata_isRepoPrivate)
                        .exists(Application.Fields.gitApplicationMetadata_gitAuth)
                        .equal(Application.Fields.workspaceId, workspaceId))
                .permission(aclPermission)
                .all();
    }

    @Override
    public Mono<Application> getApplicationByDefaultApplicationIdAndDefaultBranch(String defaultApplicationId) {

        return queryBuilder()
                .criteria(bridge().equal(
                                Application.Fields.gitApplicationMetadata_defaultApplicationId, defaultApplicationId))
                .one();
    }

    @Override
    public Mono<Integer> setAppTheme(
            String applicationId, String editModeThemeId, String publishedModeThemeId, AclPermission aclPermission) {
        Update updateObj = new Update();
        if (StringUtils.hasLength(editModeThemeId)) {
            updateObj = updateObj.set(Application.Fields.editModeThemeId, editModeThemeId);
        }
        if (StringUtils.hasLength(publishedModeThemeId)) {
            updateObj = updateObj.set(Application.Fields.publishedModeThemeId, publishedModeThemeId);
        }

        return queryBuilder().byId(applicationId).permission(aclPermission).updateFirst(updateObj);
    }

    @Override
    public Mono<Long> countByNameAndWorkspaceId(String applicationName, String workspaceId, AclPermission permission) {
        return queryBuilder()
                .criteria(bridge().equal(Application.Fields.workspaceId, workspaceId)
                        .equal(Application.Fields.name, applicationName))
                .permission(permission)
                .count();
    }

    @Override
    public Flux<String> getAllApplicationIdsInWorkspaceAccessibleToARoleWithPermission(
            String workspaceId, AclPermission permission, String permissionGroupId) {
        Criteria workspaceIdCriteria =
                Criteria.where(Application.Fields.workspaceId).is(workspaceId);

        // Check if the permission is being provided by the given permission group
        Criteria permissionGroupCriteria = Criteria.where(BaseDomain.Fields.policies)
                .elemMatch(Criteria.where("permissionGroups")
                        .in(permissionGroupId)
                        .and("permission")
                        .is(permission.getValue()));

        return queryBuilder()
                .criteria(workspaceIdCriteria, permissionGroupCriteria)
                .fields(Application.Fields.id)
                .all()
                .map(application -> application.getId());
    }

    @Override
    public Mono<Long> getAllApplicationsCountAccessibleToARoleWithPermission(
            AclPermission permission, String permissionGroupId) {

        Criteria permissionGroupCriteria = Criteria.where(BaseDomain.Fields.policies)
                .elemMatch(Criteria.where("permissionGroups")
                        .in(permissionGroupId)
                        .and("permission")
                        .is(permission.getValue()));

        return queryBuilder().criteria(permissionGroupCriteria).count();
    }

    @Override
    public Mono<Integer> unprotectAllBranches(String applicationId, AclPermission permission) {
        String isProtectedFieldPath = Application.Fields.gitApplicationMetadata_isProtectedBranch;

        Update unsetProtected = new Update().set(isProtectedFieldPath, false);

        return queryBuilder()
                .criteria(bridge().equal(Application.Fields.gitApplicationMetadata_defaultApplicationId, applicationId))
                .permission(permission)
                .updateAll(unsetProtected);
    }

    /**
     * This method sets protected=true to the Applications whose branch names are present in the given branchNames list.
     *
     * @param applicationId default Application id which is stored in git Application Meta data
     * @param branchNames   list of branches to be protected
     * @return Mono<Void>
     */
    @Override
    public Mono<Integer> protectBranchedApplications(
            String applicationId, List<String> branchNames, AclPermission permission) {
        String isProtectedFieldPath = Application.Fields.gitApplicationMetadata_isProtectedBranch;

        String branchNameFieldPath = Application.Fields.gitApplicationMetadata_branchName;

        Criteria defaultApplicationIdCriteria = Criteria.where(
                        Application.Fields.gitApplicationMetadata_defaultApplicationId)
                .is(applicationId);
        Criteria branchMatchCriteria = Criteria.where(branchNameFieldPath).in(branchNames);
        Update setProtected = new Update().set(isProtectedFieldPath, true);

        return queryBuilder()
                .criteria(defaultApplicationIdCriteria, branchMatchCriteria)
                .permission(permission)
                .updateAll(setProtected);
    }
}
