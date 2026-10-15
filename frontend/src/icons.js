import {
  ArrowDown,
  ArrowRight,
  Avatar,
  Bell,
  CircleCheck,
  Close,
  Connection,
  DataLine,
  DocumentChecked,
  EditPen,
  Expand,
  FirstAidKit,
  Fold,
  Plus,
  Reading,
  Search,
  Setting,
  SwitchButton,
  User,
  UserFilled,
  Warning,
  WarningFilled
} from '@element-plus/icons-vue'

// Dynamic menu/statistic icons and template icons without local imports need
// global registration. Local imports are resolved by their page component.
export const appIcons = Object.freeze({
  ArrowDown,
  ArrowRight,
  Avatar,
  Bell,
  CircleCheck,
  Close,
  Connection,
  DataLine,
  DocumentChecked,
  EditPen,
  Expand,
  FirstAidKit,
  Fold,
  Plus,
  Reading,
  Search,
  Setting,
  SwitchButton,
  User,
  UserFilled,
  Warning,
  WarningFilled
})

export const installAppIcons = (app) => {
  for (const [name, component] of Object.entries(appIcons)) {
    app.component(name, component)
  }
}
