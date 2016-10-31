package com.uxin.mybatis.paginator;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.MappedStatement.Builder;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.uxin.mybatis.paginator.dialect.Dialect;
import com.uxin.mybatis.paginator.dialect.MySQLDialect;
import com.uxin.mybatis.paginator.domain.PageBounds;
import com.uxin.mybatis.paginator.domain.PageList;
import com.uxin.mybatis.paginator.domain.Paginator;
import com.uxin.mybatis.paginator.support.PropertiesHelper;
import com.uxin.mybatis.paginator.support.SQLHelp;

/**
 * MyBatis提供基于mysql的分页查询的插件 将拦截Executor.query()方法实现分页.
 *
 * @author smartlv
 */

@Intercepts({ @Signature(type = Executor.class, method = "query", args = { MappedStatement.class, Object.class,
        RowBounds.class, ResultHandler.class }) })
public class OffsetLimitInterceptor implements Interceptor
{
    private static Logger logger = LoggerFactory.getLogger(OffsetLimitInterceptor.class);

    private static final ObjectFactory DEFAULT_OBJECT_FACTORY = new DefaultObjectFactory();
    private static final ObjectWrapperFactory DEFAULT_OBJECT_WRAPPER_FACTORY = new DefaultObjectWrapperFactory();
    private static final ReflectorFactory DEFAULT_REFLECTOR_FACTORY = new DefaultReflectorFactory();

    private static int MAPPED_STATEMENT_INDEX = 0;
    private static int PARAMETER_INDEX = 1;
    private static int ROWBOUNDS_INDEX = 2;
    private static int RESULT_HANDLER_INDEX = 3;

    private static ExecutorService Pool;
    private String dialectClass;
    private boolean asyncTotalCount = false;

    public Object intercept(final Invocation invocation) throws Throwable
    {
        final Executor executor = (Executor) invocation.getTarget();
        final Object[] queryArgs = invocation.getArgs();
        final MappedStatement ms = (MappedStatement) queryArgs[MAPPED_STATEMENT_INDEX];
        final Object parameter = queryArgs[PARAMETER_INDEX];
        final RowBounds rowBounds = (RowBounds) queryArgs[ROWBOUNDS_INDEX];
        final PageBounds pageBounds = new PageBounds(rowBounds);

        if (pageBounds.getOffset() == RowBounds.NO_ROW_OFFSET && pageBounds.getLimit() == RowBounds.NO_ROW_LIMIT
                && pageBounds.getOrders().isEmpty())
        {
            return invocation.proceed();
        }
        // 写死了，不考虑其他类型数据库
        final Dialect dialect = new MySQLDialect(ms, parameter, pageBounds);

        final BoundSql boundSql = ms.getBoundSql(parameter);

        queryArgs[MAPPED_STATEMENT_INDEX] = copyFromNewSql(ms, boundSql, dialect.getPageSQL());
        // queryArgs[PARAMETER_INDEX] = dialect.getParameterObject();
        queryArgs[ROWBOUNDS_INDEX] = new RowBounds(RowBounds.NO_ROW_OFFSET, RowBounds.NO_ROW_LIMIT);

        Boolean async = pageBounds.getAsyncTotalCount() == null ? asyncTotalCount : pageBounds.getAsyncTotalCount();

        // List<E> query
        Future<List<?>> listFuture = call(() -> invocation.proceed(), async);

        if (pageBounds.isContainsTotalCount())
        {
            Callable<Paginator> countTask = () -> {
                Integer count;
                Cache cache = ms.getCache();
                if (cache != null && ms.isUseCache())
                {
                    CacheKey cacheKey = executor.createCacheKey(
                            ms,
                            parameter,
                            new PageBounds(),
                            copyFromBoundSql(ms, boundSql, dialect.getCountSQL(), boundSql.getParameterMappings(),
                                    boundSql.getParameterObject()));
                    count = (Integer) cache.getObject(cacheKey);
                    if (count == null)
                    {
                        count = SQLHelp.getCount(ms, parameter, boundSql, dialect);
                        cache.putObject(cacheKey, count);
                    }
                }
                else
                {
                    count = SQLHelp.getCount(ms, parameter, boundSql, dialect);
                }
                return new Paginator(pageBounds.getPage(), pageBounds.getLimit(), count);
            };
            Future<Paginator> countFutrue = call(countTask, async);
            return new PageList(listFuture.get(), countFutrue.get());
        }

        return listFuture.get();
    }

    private <T> Future<T> call(Callable callable, boolean async)
    {
        if (async)
        {
            return Pool.submit(callable);
        }
        else
        {
            FutureTask<T> future = new FutureTask(callable);
            future.run();
            return future;
        }
    }

    private MappedStatement copyFromNewSql(MappedStatement ms, BoundSql boundSql, String sql)
    {
        // BoundSql newBoundSql = copyFromBoundSql(ms, boundSql, sql, parameterMappings, parameter);
        MetaObject metaBoundSql = MetaObject.forObject(boundSql, DEFAULT_OBJECT_FACTORY,
                DEFAULT_OBJECT_WRAPPER_FACTORY, DEFAULT_REFLECTOR_FACTORY);

        metaBoundSql.setValue("sql", sql);
        return copyFromMappedStatement(ms, new BoundSqlSqlSource(boundSql));
    }

    private BoundSql copyFromBoundSql(MappedStatement ms, BoundSql boundSql, String sql,
            List<ParameterMapping> parameterMappings, Object parameter)
    {
        BoundSql newBoundSql = new BoundSql(ms.getConfiguration(), sql, parameterMappings, parameter);
        for (ParameterMapping mapping : boundSql.getParameterMappings())
        {
            String prop = mapping.getProperty();
            if (boundSql.hasAdditionalParameter(prop))
            {
                newBoundSql.setAdditionalParameter(prop, boundSql.getAdditionalParameter(prop));
            }
        }
        return newBoundSql;
    }

    // see: MapperBuilderAssistant
    private MappedStatement copyFromMappedStatement(MappedStatement ms, SqlSource newSqlSource)
    {
        Builder builder = new Builder(ms.getConfiguration(), ms.getId(), newSqlSource, ms.getSqlCommandType());

        builder.resource(ms.getResource());
        builder.fetchSize(ms.getFetchSize());
        builder.statementType(ms.getStatementType());
        builder.keyGenerator(ms.getKeyGenerator());
        if (ms.getKeyProperties() != null && ms.getKeyProperties().length != 0)
        {
            StringBuilder keyProperties = new StringBuilder();
            for (String keyProperty : ms.getKeyProperties())
            {
                keyProperties.append(keyProperty).append(",");
            }
            keyProperties.delete(keyProperties.length() - 1, keyProperties.length());
            builder.keyProperty(keyProperties.toString());
        }

        // setStatementTimeout()
        builder.timeout(ms.getTimeout());

        // setStatementResultMap()
        builder.parameterMap(ms.getParameterMap());

        // setStatementResultMap()
        builder.resultMaps(ms.getResultMaps());
        builder.resultSetType(ms.getResultSetType());

        // setStatementCache()
        builder.cache(ms.getCache());
        builder.flushCacheRequired(ms.isFlushCacheRequired());
        builder.useCache(ms.isUseCache());

        return builder.build();
    }

    public Object plugin(Object target)
    {
        return Plugin.wrap(target, this);
    }

    public void setProperties(Properties properties)
    {
        PropertiesHelper propertiesHelper = new PropertiesHelper(properties);
        String dialectClass = propertiesHelper.getRequiredString("dialectClass");
        setDialectClass(dialectClass);

        setAsyncTotalCount(propertiesHelper.getBoolean("asyncTotalCount", false));

        setPoolMaxSize(propertiesHelper.getInt("poolMaxSize", 0));

    }

    public static class BoundSqlSqlSource implements SqlSource
    {
        BoundSql boundSql;

        public BoundSqlSqlSource(BoundSql boundSql)
        {
            this.boundSql = boundSql;
        }

        public BoundSql getBoundSql(Object parameterObject)
        {
            return boundSql;
        }
    }

    public void setDialectClass(String dialectClass)
    {
        logger.debug("dialectClass: {} ", dialectClass);
        this.dialectClass = dialectClass;
    }

    public void setAsyncTotalCount(boolean asyncTotalCount)
    {
        logger.debug("asyncTotalCount: {} ", asyncTotalCount);
        this.asyncTotalCount = asyncTotalCount;
    }

    public void setPoolMaxSize(int poolMaxSize)
    {

        if (poolMaxSize > 0)
        {
            logger.debug("poolMaxSize: {} ", poolMaxSize);
            Pool = Executors.newFixedThreadPool(poolMaxSize);
        }
        else
        {
            Pool = Executors.newCachedThreadPool();
        }

    }
}
