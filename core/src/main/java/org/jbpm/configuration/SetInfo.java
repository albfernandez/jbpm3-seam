/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jbpm.configuration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jbpm.util.XmlUtil;
import org.w3c.dom.Element;

public class SetInfo extends AbstractObjectInfo {

  private static final long serialVersionUID = 1L;

  private final ObjectInfo[] elementInfos;

  public SetInfo(Element listElement, ObjectFactoryParser configParser) {
    super(listElement, configParser);

    List elementElements = XmlUtil.elements(listElement);
    elementInfos = new ObjectInfo[elementElements.size()];
    for (int i = 0; i < elementElements.size(); i++) {
      Element elementElement = (Element) elementElements.get(i);
      elementInfos[i] = configParser.parse(elementElement);
    }
  }

  public Object createObject(ObjectFactoryImpl objectFactory) {
    Set set = new HashSet();
    if (elementInfos != null) {
      for (int i = 0; i < elementInfos.length; i++) {
        Object element = objectFactory.getObject(elementInfos[i]);
        set.add(element);
      }
    }
    return set;
  }

}
