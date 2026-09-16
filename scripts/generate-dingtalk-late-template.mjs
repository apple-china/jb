import fs from 'node:fs';

const sourcePath = 'artifacts/化妆预约V1.0_优化导入版-v10.json';
const outputPath = 'artifacts/化妆迟到提醒_极简版_可导入.json';
const outer = JSON.parse(fs.readFileSync(sourcePath, 'utf8'));
const editor = JSON.parse(outer.editorData);
const clone = (value) => structuredClone(value);

function find(node, componentName) {
  if (node.componentName === componentName) return node;
  for (const child of node.children ?? []) {
    const result = find(child, componentName);
    if (result) return result;
  }
  return undefined;
}

const root = clone(editor.schema.componentsTree[0]);
const baseText = find(root, 'BaseText');
const baseTag = find(root, 'Tag');
const originalHeader = root.children[0];
if (!baseText || !baseTag || originalHeader?.componentName !== 'ColumnLayout') {
  throw new Error('Source template does not contain the required base components.');
}

function textNode(id, title, variable, options = {}) {
  const node = clone(baseText);
  node.id = id;
  node.title = title;
  node.props.text = { i18n: false, type: 'dynamicString', content: `\${${variable}}` };
  node.props.hoverText = { i18n: false, type: 'dynamicString', content: title };
  node.props.marginLeft = options.marginLeft ?? 12;
  node.props.marginRight = options.marginRight ?? 12;
  node.props.marginTop = options.marginTop ?? 0;
  node.props.marginBottom = options.marginBottom ?? 0;
  node.props.gravity = options.gravity ?? 'left';
  node.props.customFontSize = options.fontSize ?? 14;
  node.props.customFontLineHeight = options.lineHeight ?? 21;
  node.props.bold = options.bold ?? false;
  node.props.maxLine = {
    type: 'dynamicNumber', valueType: 'fixed', value: options.maxLines ?? 1,
    variable: '', variableType: 'global'
  };
  node.props.color = options.color ?? 'black';
  node.props.customLightColor = {
    type: 'dynamicColor', valueType: 'fixed', value: options.lightColor ?? '#1F2329',
    variable: '', variableType: 'global'
  };
  node.props.customDarkColor = {
    type: 'dynamicColor', valueType: 'fixed', value: options.darkColor ?? '#F5F5F5',
    variable: '', variableType: 'global'
  };
  node.props.enableIcon = false;
  return node;
}

function tagNode(id, title, variable) {
  const node = clone(baseTag);
  node.id = id;
  node.title = title;
  node.props.text = { i18n: false, type: 'dynamicString', content: `\${${variable}}` };
  node.props.color = {
    type: 'dynamicSelect', valueType: 'fixed', value: 'blue',
    variable: '', variableType: 'global'
  };
  node.props.marginLeft = 0;
  node.props.marginRight = 0;
  node.props.marginTop = 0;
  node.props.marginBottom = 0;
  return node;
}
const header = clone(originalHeader);
header.id = 'late_header';
header.title = '标题与预约时间';
header.props.columnWidth = [
  { widthMode: 'weighted', weight: 3, width: 70 },
  { widthMode: 'weighted', weight: 1, width: 30 }
];
header.props.columnSpacing = 8;
header.props.marginLeft = 4;
header.props.marginRight = 4;
header.children[0].id = 'late_header_title_column';
header.children[0].props.paddingLeft.value = 8;
header.children[0].props.paddingRight.value = 4;
header.children[0].children = [tagNode('late_header_title', '预约提醒', 'header_title')];

header.children[1].id = 'late_header_time_column';
header.children[1].props.paddingLeft.value = 4;
header.children[1].props.paddingRight.value = 12;
header.children[1].props.childGravity = 'rightTop';
header.children[1].children = [textNode('late_header_time', '预约时间', 'appointment_time', {
  marginLeft: 0, marginRight: 4, gravity: 'right', fontSize: 13, lineHeight: 22,
  color: 'gray', lightColor: '#8A8F99', darkColor: '#A6AAB3'
})];

const divider = {
  componentName: 'Divider',
  id: 'late_header_divider',
  props: {
    visible: { type: 'dynamicVisible', value: true, valueType: 'fixed', condition: { op: 'and', conditions: [] } },
    marginLeft: 12,
    marginRight: 12,
    marginTop: 0,
    marginBottom: 0,
    lineStyle: 'solid',
    lineColor: '#E6E8EB',
    darkModeLineColor: '#3A3D42',
    margin: -2,
    innerOffset: 0
  },
  title: '分割线',
  hidden: false,
  isLocked: false,
  condition: true,
  conditionGroup: ''
};

root.id = 'late_reminder_card';
root.title = '化妆迟到提醒';
root.children = [
  header,
  divider,
  textNode('late_mention', '@主播与称呼', 'mention_text', {
    marginTop: 10, fontSize: 15, lineHeight: 22, bold: true
  }),
  textNode('late_content', '提醒内容', 'reminder_text', {
    marginTop: 4, fontSize: 14, lineHeight: 22, maxLines: 4
  }),
  textNode('late_appointment', '化妆师与团队', 'appointment_text', {
    marginTop: 10, marginBottom: 8, fontSize: 11, lineHeight: 16,
    color: 'gray', lightColor: '#8A8F99', darkColor: '#A6AAB3'
  })
];

const usedComponents = new Set(['Card', 'ColumnLayout', 'Column', 'BaseText', 'Divider', 'Tag']);
editor.schema.componentsMap = editor.schema.componentsMap.filter((item) =>
  usedComponents.has(item.componentName));
editor.schema.componentsTree = [root];
editor.variableList = [
  { name: 'header_title', private: false, type: 'string', id: 'header_title', description: '顶部标题', editorVarType: 'variables' },
  { name: 'appointment_time', private: false, type: 'string', id: 'appointment_time', description: '预约时间', editorVarType: 'variables' },
  { name: 'mention_text', private: false, type: 'string', id: 'mention_text', description: '@主播与随机称呼', editorVarType: 'variables' },
  { name: 'reminder_text', private: false, type: 'string', id: 'reminder_text', description: '随机提醒内容与表情', editorVarType: 'variables' },
  { name: 'appointment_text', private: false, type: 'string', id: 'appointment_text', description: '化妆师与团队', editorVarType: 'variables' }
];
editor.mockData = {
  cardData: {
    header_title: '预约提醒',
    appointment_time: '08:30',
    mention_text: '@玲玲  姐姐，',
    reminder_text: '化妆老师在等你呢～请尽快到司签到 ✨💄',
    appointment_text: '小贝老师 · 星河一团'
  },
  cardPrivateData: {},
  localData: {},
  richTextData: {}
};
editor.pageData = {};
outer.editorData = JSON.stringify(editor);
outer.widgetInfo = '';
outer.type = 'im';
outer.mode = 'card';
fs.writeFileSync(outputPath, JSON.stringify(outer), 'utf8');
console.log(outputPath);
