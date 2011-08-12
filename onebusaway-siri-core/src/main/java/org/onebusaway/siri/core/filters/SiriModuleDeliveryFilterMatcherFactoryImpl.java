/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.siri.core.filters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.onebusaway.collections.tuple.T2;
import org.onebusaway.collections.tuple.Tuples;
import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.siri.core.exceptions.SiriException;

import uk.org.siri.siri.AbstractSubscriptionStructure;
import uk.org.siri.siri.MessageQualifierStructure;
import uk.org.siri.siri.ParticipantRefStructure;
import uk.org.siri.siri.SubscriptionRequest;

public class SiriModuleDeliveryFilterMatcherFactoryImpl {

  private static final String ARG_MATCH_PREFIX = "Match.";

  private static final String ARG_MODULE_TYPE = "ModuleType";

  private static final Pattern _startsWithPattern = Pattern.compile("^StartsWith\\((.*)\\)$");

  private static final Pattern _endsWithPattern = Pattern.compile("^EndsWith\\((.*)\\)$");

  private static final Pattern _regexPattern = Pattern.compile("^Regex\\((.*)\\)$");

  private static final String EMPTY_VALUE_MATCHER = "Empty()";

  private static final Map<String, ValueExtractor> _valueExtractors = new HashMap<String, ValueExtractor>() {
    private static final long serialVersionUID = 1L;
    {
      put("Address", new AddressExtractor());
      put("ConsumerAddress", new ConsumerAddressExtractor());
      put("SubscriptionFilterIdentifier",
          new SubscriptionFilterIdentifierExtractor());
      put("RequestorRef", new RequestorRefExtractor());
      put("MessageIdentifier", new MessageIdentifierExtractor());
    }
  };

  public SiriModuleDeliveryFilterMatcher create(Map<String, String> filterArgs) {

    List<T2<ValueExtractor, ValueMatcher>> matchers = new ArrayList<T2<ValueExtractor, ValueMatcher>>();

    for (Iterator<Map.Entry<String, String>> it = filterArgs.entrySet().iterator(); it.hasNext();) {

      Map.Entry<String, String> entry = it.next();

      String key = entry.getKey();
      String value = entry.getValue();

      if (!key.startsWith(ARG_MATCH_PREFIX))
        continue;

      it.remove();

      key = key.substring(ARG_MATCH_PREFIX.length());

      if (key.equals(ARG_MODULE_TYPE))
        continue;

      ValueExtractor valueExtractor = _valueExtractors.get(key);
      ValueMatcher valueMatcher = getValueMatcherForValue(value);

      if (valueExtractor == null)
        throw new SiriException("no matcher found for property \"" + key + "\"");

      T2<ValueExtractor, ValueMatcher> tuple = Tuples.tuple(valueExtractor,
          valueMatcher);
      matchers.add(tuple);
    }

    ESiriModuleType moduleType = null;

    String moduleTypeString = filterArgs.remove(ARG_MODULE_TYPE);
    if (moduleTypeString != null)
      moduleType = ESiriModuleType.valueOf(moduleTypeString);

    return new SiriModuleDeliveryFilterMatcherImpl(moduleType,
        new ArrayList<T2<ValueExtractor, ValueMatcher>>(matchers));
  }

  private ValueMatcher getValueMatcherForValue(String value) {

    Matcher m1 = _startsWithPattern.matcher(value);
    if (m1.matches())
      return new StartsWithMatcher(m1.group(1));

    Matcher m2 = _endsWithPattern.matcher(value);
    if (m2.matches())
      return new EndsWithMatcher(m2.group(1));

    Matcher m3 = _regexPattern.matcher(value);
    if (m3.matches())
      return new RegexMatcher(m3.group(1));

    if (value.equals(EMPTY_VALUE_MATCHER))
      return new EmptyMatcher();

    return new DirectMatcher(value);
  }

  /****
   * 
   ****/

  private static class SiriModuleDeliveryFilterMatcherImpl implements
      SiriModuleDeliveryFilterMatcher {

    private final ESiriModuleType _moduleType;

    private final List<T2<ValueExtractor, ValueMatcher>> _matchers;

    public SiriModuleDeliveryFilterMatcherImpl(ESiriModuleType moduleType,
        List<T2<ValueExtractor, ValueMatcher>> matchers) {
      _moduleType = moduleType;
      _matchers = matchers;
    }

    @Override
    public boolean isMatch(SubscriptionRequest subscriptionRequest,
        ESiriModuleType moduleType,
        AbstractSubscriptionStructure moduleTypeSubscriptionRequest) {

      if (_moduleType != null && _moduleType != moduleType)
        return false;

      for (T2<ValueExtractor, ValueMatcher> tuple : _matchers) {
        ValueExtractor extractor = tuple.getFirst();
        ValueMatcher matcher = tuple.getSecond();
        String value = extractor.extractValue(subscriptionRequest, moduleType,
            moduleTypeSubscriptionRequest);
        if (!matcher.isMatch(value))
          return false;
      }

      return true;
    }
  }

  /****
   * 
   ****/

  private static interface ValueExtractor {
    public String extractValue(SubscriptionRequest subscriptionRequest,
        ESiriModuleType moduleType,
        AbstractSubscriptionStructure moduleTypeSubscriptionRequest);
  }

  private static class AddressExtractor implements ValueExtractor {

    @Override
    public String extractValue(SubscriptionRequest subscriptionRequest,
        ESiriModuleType moduleType,
        AbstractSubscriptionStructure moduleTypeSubscriptionRequest) {
      return subscriptionRequest.getAddress();
    }
  }

  private static class ConsumerAddressExtractor implements ValueExtractor {

    @Override
    public String extractValue(SubscriptionRequest subscriptionRequest,
        ESiriModuleType moduleType,
        AbstractSubscriptionStructure moduleTypeSubscriptionRequest) {
      return subscriptionRequest.getConsumerAddress();
    }
  }

  private static class SubscriptionFilterIdentifierExtractor implements
      ValueExtractor {

    @Override
    public String extractValue(SubscriptionRequest subscriptionRequest,
        ESiriModuleType moduleType,
        AbstractSubscriptionStructure moduleTypeSubscriptionRequest) {
      return subscriptionRequest.getSubscriptionFilterIdentifier();
    }
  }

  private static class RequestorRefExtractor implements ValueExtractor {

    @Override
    public String extractValue(SubscriptionRequest subscriptionRequest,
        ESiriModuleType moduleType,
        AbstractSubscriptionStructure moduleTypeSubscriptionRequest) {
      ParticipantRefStructure ref = subscriptionRequest.getRequestorRef();
      if (ref == null)
        return null;
      return ref.getValue();
    }
  }

  private static class MessageIdentifierExtractor implements ValueExtractor {

    @Override
    public String extractValue(SubscriptionRequest subscriptionRequest,
        ESiriModuleType moduleType,
        AbstractSubscriptionStructure moduleTypeSubscriptionRequest) {
      MessageQualifierStructure ref = subscriptionRequest.getMessageIdentifier();
      if (ref == null)
        return null;
      return ref.getValue();
    }
  }

  /****
   * 
   ****/

  private static interface ValueMatcher {
    public boolean isMatch(String argument);
  }

  private static class DirectMatcher implements ValueMatcher {

    private final String _value;

    public DirectMatcher(String value) {
      _value = value;
    }

    @Override
    public boolean isMatch(String argument) {
      return _value.equals(argument);
    }
  }

  private static class StartsWithMatcher implements ValueMatcher {

    private final String _prefix;

    public StartsWithMatcher(String prefix) {
      _prefix = prefix;
    }

    @Override
    public boolean isMatch(String argument) {
      return argument.startsWith(_prefix);
    }
  }

  private static class EndsWithMatcher implements ValueMatcher {

    private final String _suffix;

    public EndsWithMatcher(String suffix) {
      _suffix = suffix;
    }

    @Override
    public boolean isMatch(String argument) {
      return argument.endsWith(_suffix);
    }
  }

  private static class RegexMatcher implements ValueMatcher {

    private final String _regex;

    public RegexMatcher(String regex) {
      _regex = regex;
    }

    @Override
    public boolean isMatch(String argument) {
      return argument.matches(_regex);
    }
  }

  private static class EmptyMatcher implements ValueMatcher {

    @Override
    public boolean isMatch(String argument) {
      return argument == null || argument.isEmpty();
    }
  }

}
