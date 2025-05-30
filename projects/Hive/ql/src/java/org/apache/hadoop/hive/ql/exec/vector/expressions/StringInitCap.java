/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.exec.vector.expressions;

import org.apache.hadoop.hive.ql.util.WordUtils;
import org.apache.hadoop.io.Text;

/**
 * Returns str, with the first letter of each word in uppercase, all other
 * letters in lowercase. Words are delimited by white space. e.g. initcap('the
 * soap') returns 'The Soap'
 * Extends {@link StringUnaryUDF}.
 */
public class StringInitCap extends StringUnaryUDF {
  private static final long serialVersionUID = 1L;

  public StringInitCap(int colNum, int outputColumn) {
    super(colNum, outputColumn, new IUDFUnaryString() {

      Text t = new Text();

      @Override
      public Text evaluate(Text s) {
        if (s == null) {
          return null;
        }
        t.set(WordUtils.capitalizeFully(s.toString()));
        return t;
      }
    });
  }

  public StringInitCap() {
    super();
  }
}