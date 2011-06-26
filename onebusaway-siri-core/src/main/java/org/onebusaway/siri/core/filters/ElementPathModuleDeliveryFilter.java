package org.onebusaway.siri.core.filters;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;

import org.onebusaway.siri.core.exceptions.SiriException;
import org.onebusaway.siri.core.versioning.PropertyConverterSupport;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.ServiceDelivery;

public class ElementPathModuleDeliveryFilter implements
    SiriModuleDeliveryFilter {

  private final String[] _propertyNames;

  /**
   * See notes on lazy introspection below for details on why this is volatile
   * (also see "Effective Java" for details of thread-safe lazy initialization.
   */
  private volatile PropertyDescriptor[] _properties = null;

  private final Object _value;

  public ElementPathModuleDeliveryFilter(String expression, Object value) {
    _propertyNames = expression.split("\\.");
    for (int i = 0; i < _propertyNames.length; i++) {
      String name = _propertyNames[i];
      _propertyNames[i] = name.substring(0, 1).toLowerCase()
          + name.substring(1);
    }
    _properties = new PropertyDescriptor[_propertyNames.length];
    _value = value;
  }

  @Override
  public AbstractServiceDeliveryStructure filter(ServiceDelivery delivery,
      AbstractServiceDeliveryStructure moduleDelivery) {

    traversePropertyPath(moduleDelivery, 0);

    return moduleDelivery;
  }

  private void traversePropertyPath(Object value, int depth) {

    PropertyDescriptor property = getPropertyDescriptorForDepth(value, depth);

    if (depth < _propertyNames.length - 1) {

      Method readMethod = property.getReadMethod();
      Object subValue = PropertyConverterSupport.getSourcePropertyValue(value,
          readMethod);

      /**
       * If the value is null, we exit the recursive loop, because we don't have
       * a target object to apply the filter to
       */
      if (subValue == null)
        return;

      if (subValue instanceof Collection<?>) {
        Collection<?> collection = (Collection<?>) subValue;
        for (Object element : collection)
          traversePropertyPath(element, depth + 1);
      } else {
        traversePropertyPath(subValue, depth + 1);
      }

    } else {
      applyFilter(value, property);
    }
  }

  /**
   * We support lazy initialization of the {@link PropertyDescriptor}
   * descriptors necessary to read the and write to our object tree. Why?
   * 
   * We can't do introspection ahead of time because we support iteration over
   * Collection types. Specifically, if a path expression navigates over a
   * collection, we'll iterate over the values of the collection, applying the
   * next level of property path to the collection value, as opposed to the
   * collection object itself. Because we can't introspect the element type of a
   * Collection from the parent class, we have to wait to do introspection at
   * runtime on the values of the Collection itself.
   * 
   * @param value
   * @param depth
   * @return
   */
  private PropertyDescriptor getPropertyDescriptorForDepth(Object value,
      int depth) {

    PropertyDescriptor property = _properties[depth];

    if (property == null) {

      synchronized (this) {

        if (property == null) {

          String propertyName = _propertyNames[depth];

          try {

            BeanInfo beanInfo = Introspector.getBeanInfo(value.getClass());

            for (PropertyDescriptor propertyDesc : beanInfo.getPropertyDescriptors()) {
              if (propertyDesc.getName().equals(propertyName)) {
                property = propertyDesc;
                break;
              }
            }

          } catch (Throwable ex) {
            throw new SiriException("error in introspection of class "
                + value.getClass() + " and property \"" + propertyName + "\"");
          }

          if (property == null) {
            throw new SiriException("class " + value.getClass()
                + " does not have property \"" + propertyName + "\"");
          }

          PropertyDescriptor[] properties = new PropertyDescriptor[_properties.length];
          System.arraycopy(_properties, 0, properties, 0, properties.length);
          properties[depth] = property;
          _properties = properties;
        }
      }
    }

    return property;
  }

  private void applyFilter(Object parent, PropertyDescriptor propertyDescriptor) {

    Method readMethod = propertyDescriptor.getReadMethod();
    Method writeMethod = propertyDescriptor.getWriteMethod();

    if (writeMethod != null) {
      PropertyConverterSupport.setTargetPropertyValue(parent, writeMethod,
          _value);
    } else if (Collection.class.isAssignableFrom(propertyDescriptor.getPropertyType())
        && (_value == null || _value instanceof Collection)) {
      Collection<?> values = (Collection<?>) _value;
      if (values == null)
        values = Collections.emptyList();
      PropertyConverterSupport.setTargetPropertyValues(parent, readMethod,
          values);
    } else {
      throw new SiriException("no write method for property \""
          + propertyDescriptor.getName() + " on " + parent);
    }
  }
}
