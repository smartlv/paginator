/*
 * Copyright (c) 2012-2013, Poplar Yfyang 杨友峰 (poplar1123@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.uxin.mybatis.paginator.support;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.uxin.mybatis.paginator.dialect.Dialect;

/**
 * @author poplar.yfyang
 * @author miemiedev
 */
public class SQLHelp
{
    private static Logger logger = LoggerFactory.getLogger(SQLHelp.class);

    /**
     * 查询总纪录数
     *
     * @param mappedStatement
     *        mapped
     * @param parameterObject
     *        参数
     * @param boundSql
     *        boundSql
     * @param dialect
     *        database dialect
     * @return 总记录数
     * @throws java.sql.SQLException
     *         sql查询错误
     */
    public static int getCount(final MappedStatement mappedStatement, final Object parameterObject,
            final BoundSql boundSql, Dialect dialect) throws SQLException
    {

        final String count_sql = dialect.getCountSQL();
        logger.debug("Total count SQL [{}] ", count_sql);
        logger.debug("Total count Parameters: {} ", parameterObject);

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try
        {
            con = mappedStatement.getConfiguration().getEnvironment().getDataSource().getConnection();
            pstmt = con.prepareStatement(count_sql);
            DefaultParameterHandler handler = new DefaultParameterHandler(mappedStatement, parameterObject, boundSql);
            handler.setParameters(pstmt);

            rs = pstmt.executeQuery();
            int count = 0;
            if (rs.next())
            {
                count = rs.getInt(1);
            }
            logger.debug("Total count: {}", count);
            return count;
        }
        catch (Exception e)
        {
            logger.error("", e);
            return 0;
        }
        finally
        {
            try
            {
                if (rs != null)
                {
                    rs.close();
                }
            }
            finally
            {
                try
                {
                    if (pstmt != null)
                    {
                        pstmt.close();
                    }
                }
                finally
                {
                    if (con != null && !con.isClosed())
                    {
                        con.close();
                    }
                }
            }
        }
    }
}
