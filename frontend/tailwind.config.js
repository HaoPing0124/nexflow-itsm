/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        canvas: '#000000',
        surface: '#0A0A0A',
        elevated: '#141414',
        primary: '#FAFAFA',
        secondary: '#A3A3A3',
        muted: '#737373',
        brand: '#FFFFFF',
        branddeep: '#E5E5E5',
        /* Structural palette stays monochrome; these four are the only hues,
           reserved for status / priority / SLA meaning. */
        success: '#4ADE80',
        warning: '#FBBF24',
        danger: '#F87171',
        info: '#60A5FA',
        disabled: '#525252',
      },
      fontFamily: {
        heading: ['"Instrument Serif"', '"Songti SC"', 'serif'],
        body: ['Barlow', '"PingFang SC"', '"Microsoft YaHei"', 'sans-serif'],
        data: ['"JetBrains Mono"', 'ui-monospace', 'monospace'],
      },
    },
  },
  plugins: [],
}