let reg = /^(-3|-2|-1|0|[1-5])$/
let input = document.querySelector('input[name="y"]')
let span = document.querySelector('#errorMessage')

// Canvas Fingerprinting
function getCanvasFingerprint() {
  try {
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')

    canvas.width = 200
    canvas.height = 50

    ctx.textBaseline = 'top'
    ctx.font = '14px Arial'
    ctx.fillStyle = '#f60'
    ctx.fillRect(125, 1, 62, 20)
    ctx.fillStyle = '#069'
    ctx.fillText('Fingerprint', 2, 15)
    ctx.fillStyle = 'rgba(102, 204, 0, 0.7)'
    ctx.fillText('Fingerprint', 4, 17)

    const dataUrl = canvas.toDataURL()
    let hash = 0
    for (let i = 0; i < dataUrl.length; i++) {
      const char = dataUrl.charCodeAt(i)
      hash = (hash << 5) - hash + char
      hash = hash & hash
    }

    return hash.toString()
  } catch {
    return 'canvas_error'
  }
}

// Font Fingerprinting
function getFontFingerprint() {
  return new Promise((resolve) => {
    try {
      const fontList = [
        'Arial',
        'Arial Black',
        'Arial Narrow',
        'Times New Roman',
        'Courier New',
        'Verdana',
        'Comic Sans MS',
        'Impact',
        'Georgia',
        'Tahoma',
        'Trebuchet MS',
        'Palatino',
        'Lucida Console',
        'Garamond',
        'Bookman',
        'Helvetica',
      ]

      const detectedFonts = []
      const testString = 'mmmmmmmmmmlli'
      const testSize = '72px'
      const span = document.createElement('span')

      span.style.fontSize = testSize
      span.style.position = 'absolute'
      span.style.left = '-9999px'
      span.style.top = '-9999px'
      span.textContent = testString

      document.body.appendChild(span)
      span.style.fontFamily = 'monospace'
      const defaultWidth = span.offsetWidth
      const defaultHeight = span.offsetHeight

      let checkedFonts = 0

      function checkNextFont() {
        if (checkedFonts >= fontList.length) {
          document.body.removeChild(span)
          resolve(detectedFonts)
          return
        }

        const font = fontList[checkedFonts]
        span.style.fontFamily = `${font}, monospace`

        setTimeout(() => {
          const currentWidth = span.offsetWidth
          const currentHeight = span.offsetHeight

          if (
            currentWidth !== defaultWidth ||
            currentHeight !== defaultHeight
          ) {
            detectedFonts.push(font)
          }

          checkedFonts++
          checkNextFont()
        }, 50)
      }
      checkNextFont()
    } catch {
      resolve([])
    }
  })
}

// WebRTC Leak Test
function getWebRTCInfo() {
  return new Promise((resolve) => {
    try {
      if (!window.RTCPeerConnection) {
        resolve({ hasWebRTC: false, localIPs: [] })
        return
      }

      const servers = [
        { urls: 'stun:stun.l.google.com:19302' },
        { urls: 'stun:stun1.l.google.com:19302' },
        { urls: 'stun:stun2.l.google.com:19302' },
        { urls: 'stun:stun3.l.google.com:19302' },
        { urls: 'stun:stun4.l.google.com:19302' },
      ]

      const pc = new RTCPeerConnection({
        iceServers: servers,
        iceCandidatePoolSize: 10,
      })
      const ips = new Set()
      let timeoutId

      const cleanup = () => {
        if (timeoutId) {
          clearTimeout(timeoutId)
        }
        pc.close()
      }

      pc.onicecandidate = (e) => {
        if (e.candidate) {
          const candidate = e.candidate.candidate
          const regex =
            /([0-9]{1,3}(\.[0-9]{1,3}){3}|[a-f0-9]{1,4}(:[a-f0-9]{1,4}){7})/
          const match = candidate.match(regex)

          if (match) {
            const ip = match[1]
            if (
              !ip.startsWith('192.168.') &&
              !ip.startsWith('10.') &&
              !ip.startsWith('172.') &&
              !ip.startsWith('127.') &&
              !ip.startsWith('0.') &&
              !ip.startsWith('255.') &&
              !ip.startsWith('169.254.')
            ) {
              ips.add(ip)
            }
          }
        }
      }

      timeoutId = setTimeout(() => {
        resolve({
          hasWebRTC: true,
          localIPs: Array.from(ips),
          candidateCount: ips.size,
        })
        cleanup()
      }, 3000)

      pc.createDataChannel('test')
      pc.createOffer()
        .then((offer) => pc.setLocalDescription(offer))
        .catch(() => {
          resolve({ hasWebRTC: true, localIPs: [], error: 'offer_failed' })
          cleanup()
        })
    } catch {
      resolve({ hasWebRTC: false, localIPs: [], error: 'exception' })
    }
  })
}

// Client Hints
function getClientHints() {
  try {
    return {
      deviceMemory: navigator.deviceMemory || 'unknown',
      hardwareConcurrency: navigator.hardwareConcurrency || 'unknown',
      platform: navigator.platform,
      userAgent: navigator.userAgent,
      viewport: {
        width: window.innerWidth,
        height: window.innerHeight,
      },
    }
  } catch {
    return { error: 'client_hints_error' }
  }
}

// HTTP/2 Fingerprinting
function getHTTP2Info() {
  try {
    return {
      supportsPush: 'PushManager' in window,
      supportsServiceWorker: 'serviceWorker' in navigator,
      supportsFetch: 'fetch' in window,
      supportsStreams: 'ReadableStream' in window,
    }
  } catch {
    return { error: 'http2_info_error' }
  }
}

async function sendFingerprintToServer() {
  try {
    const [fonts, webRTCInfo, canvasFingerprint] = await Promise.all([
      getFontFingerprint(),
      getWebRTCInfo(),
      Promise.resolve(getCanvasFingerprint()),
    ])
    const fullFingerprint = {
      timestamp: new Date().toISOString(),
      userAgent: navigator.userAgent,
      language: navigator.language,
      languages: navigator.languages,
      platform: navigator.platform,
      hardwareConcurrency: navigator.hardwareConcurrency,
      deviceMemory: navigator.deviceMemory,
      screen: {
        width: window.screen.width,
        height: window.screen.height,
      },
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      canvasFingerprint: canvasFingerprint,
      fonts: fonts,
      webRTC: webRTCInfo,
      clientHints: getClientHints(),
      http2: getHTTP2Info(),
    }

    const params = new URLSearchParams()
    params.append('action', 'track_user')
    params.append('fingerprint', JSON.stringify(fullFingerprint))
    await fetch(`/fcgi-bin/app.jar?${params.toString()}`, {
      method: 'GET',
    })
  } catch {
    /* empty */
  }
}

document.addEventListener('DOMContentLoaded', function () {
  loadSavedResults()
  sendFingerprintToServer()
})

document.querySelector('.btn').onclick = function (e) {
  e.preventDefault()
  if (!validate(reg, input.value)) {
    notValid(input, span, 'Вы должны ввести целое число от -3 до 5')
  } else {
    valid(input, span, '')
    getResponse(e)
  }
}

function validate(regex, inp) {
  return regex.test(inp)
}

function notValid(inp, element, message) {
  inp.classList.add('is-invalid')
  element.innerHTML = message
}

function valid(inp, element, message) {
  inp.classList.remove('is-invalid')
  inp.classList.add('is-valid')
  element.innerHTML = message
}

async function getResponse(ev) {
  ev.preventDefault()

  try {
    const xCheckboxes = document.querySelectorAll(
      'input[name="hidden-checkbox"]:checked',
    )
    const yInput = document.querySelector('input[name="y"]')
    const rRadio = document.querySelector('input[name="hidden-radio"]:checked')

    if (xCheckboxes.length === 0 || !yInput || !rRadio) {
      notValid(input, span, 'Пожалуйста, заполните все поля')
      return
    }

    const y = yInput.value
    const r = rRadio.value
    const xValues = Array.from(xCheckboxes).map((checkbox) => checkbox.value)

    const params = new URLSearchParams()
    params.append('r', r)
    params.append('y', y)
    xValues.forEach((x) => {
      params.append('x', x)
    })

    const response = await fetch(`/fcgi-bin/app.jar?${params.toString()}`, {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
    })

    if (!response.ok) {
      return response.text()
    }

    const answer = await response.text()
    localStorage.setItem('session', answer)
    let res = JSON.parse(answer)

    if (res.error === 'all ok') {
      valid(input, span, '')

      if (res.current && Array.isArray(res.current)) {
        res.current.forEach((currentData) => {
          saveResultToStorage({
            x: currentData.x,
            y: currentData.y,
            r: currentData.r,
            timestamp: currentData.timestamp || new Date().toLocaleString(),
            workTime: currentData.workTime || 0,
            hit: currentData.hit || false,
          })
        })
      } else {
        const currentData = res.current || res
        saveResultToStorage({
          x: currentData.x || xValues[0],
          y: currentData.y || y,
          r: currentData.r || r,
          timestamp: currentData.timestamp || new Date().toLocaleString(),
          workTime: currentData.workTime || 0,
          hit: currentData.hit || false,
        })
      }

      updateTable()
      const firstX = xValues[0]
      updateDotPosition(firstX, y, r)
    }
  } catch {
    // Ошибка обрабатывается без вывода в консоль
  }
}

function saveResultToStorage(resultData) {
  let savedResults = JSON.parse(localStorage.getItem('savedResults') || '[]')
  savedResults.unshift(resultData)

  if (savedResults.length > 100) {
    savedResults = savedResults.slice(0, 100)
  }

  localStorage.setItem('savedResults', JSON.stringify(savedResults))
}

function loadSavedResults() {
  const savedResults = JSON.parse(localStorage.getItem('savedResults') || '[]')

  if (savedResults.length > 0) {
    updateTable()
  }
}

function updateTable() {
  const savedResults = JSON.parse(localStorage.getItem('savedResults') || '[]')
  const table = document.getElementById('table')
  let tbody = table.getElementsByTagName('tbody')[0]

  tbody.innerHTML = ''
  savedResults.forEach((result) => {
    let row = document.createElement('tr')
    let xCell = document.createElement('td')
    let yCell = document.createElement('td')
    let rCell = document.createElement('td')
    let timeCell = document.createElement('td')
    let worktimeCell = document.createElement('td')
    let isHitCell = document.createElement('td')

    xCell.innerText = result.x
    yCell.innerText = result.y
    rCell.innerText = result.r
    timeCell.innerText = result.timestamp
    worktimeCell.innerText = result.workTime ? result.workTime + ' мс' : '0 мс'

    if (result.hit === true || result.hit === 'true') {
      isHitCell.innerText = '✓'
      isHitCell.style.color = 'green'
      isHitCell.style.fontWeight = 'bold'
    } else {
      isHitCell.innerText = '✗'
      isHitCell.style.color = 'red'
      isHitCell.style.fontWeight = 'bold'
    }
    row.appendChild(xCell)
    row.appendChild(yCell)
    row.appendChild(rCell)
    row.appendChild(timeCell)
    row.appendChild(worktimeCell)
    row.appendChild(isHitCell)

    tbody.appendChild(row)
  })
}

function updateDotPosition(x, y, r) {
  const dot = document.getElementById('dot')
  if (!dot) {
    return
  }

  const svgX = 150 + (parseFloat(x) / parseFloat(r)) * 75
  const svgY = 150 - (parseFloat(y) / parseFloat(r)) * 75

  dot.setAttribute('cx', svgX)
  dot.setAttribute('cy', svgY)
  dot.style.display = 'block'
}

/* eslint-disable */
function clearHistory() {
  localStorage.removeItem('savedResults')
  updateTable()
}
