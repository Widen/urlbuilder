package com.widen.urlbuilder;

final class StringUtilsInternal
{
    /*
     * Apache Commons Lang
     *
     * Copyright 2010 The Apache Software Foundation
     *
     * Licensed to the Apache Software Foundation (ASF) under one or more
     * contributor license agreements.  See the NOTICE file distributed with
     * this work for additional information regarding copyright ownership.
     * The ASF licenses this file to You under the Apache License, Version 2.0
     * (the "License"); you may not use this file except in compliance with
     * the License.  You may obtain a copy of the License at
     *
     *      http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */

    private StringUtilsInternal()
    {
    }

    /**
     * @return true if the String is null, empty, or only whitespace
     */
    static boolean isBlank(String str)
    {
        if (str == null)
        {
            return true;
        }
        for (int i = 0; i < str.length(); i++)
        {
            if (!Character.isWhitespace(str.charAt(i)))
            {
                return false;
            }
        }
        return true;
    }

    static boolean isNotBlank(String str)
    {
        return !isBlank(str);
    }

    /**
     * @return the String without leading and trailing characters at or below a space, or an empty String if it is null
     */
    static String trimToEmpty(String str)
    {
        return str == null ? "" : str.trim();
    }

    static boolean isEmpty(String str)
    {
        return str == null || str.isEmpty();
    }

}
